package io.github.yavonalabs.vectis.core.mutation;

import io.github.yavonalabs.vectis.core.action.EntityAction;
import io.github.yavonalabs.vectis.core.action.EntityActionRegistry;
import io.github.yavonalabs.vectis.core.annotation.RiskLevel;
import io.github.yavonalabs.vectis.core.event.VectisChangeEvent;
import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.routing.IdCodec;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Authorized action boundary. Managed local handlers execute inside the explicit
 * commit wrapper; host-managed handlers retain responsibility for their own effects.
 */
public class ActionMutationService {
    private final EntityMetadataRegistry metadata;
    private final EntityActionRegistry actions;
    private final DynamicCriteriaQueryEngine queries;
    private final AdminPermissionEvaluator permissions;
    private final MutationActorProvider actors;
    private final ApplicationEventPublisher events;
    private final ManagedActionTransaction managed;
    private final MutationReceiptStore receipts;
    private final ActionProposalStore proposals;
    private final io.github.yavonalabs.vectis.core.audit.VectisAuditLogService audit;

    public ActionMutationService(EntityMetadataRegistry metadata, EntityActionRegistry actions,
            DynamicCriteriaQueryEngine queries, AdminPermissionEvaluator permissions,
            MutationActorProvider actors, ApplicationEventPublisher events,
            ManagedActionTransaction managed, MutationReceiptStore receipts, ActionProposalStore proposals,
            io.github.yavonalabs.vectis.core.audit.VectisAuditLogService audit) {
        this.metadata = metadata;
        this.actions = actions;
        this.queries = queries;
        this.permissions = permissions;
        this.actors = actors;
        this.events = events;
        this.managed = managed;
        this.receipts = receipts;
        this.proposals = proposals;
        this.audit = audit;
    }

    public enum Outcome { COMMITTED, HOST_COMPLETED }
    public record Result(String actionLabel, Outcome outcome, boolean replayed) {}

    @SuppressWarnings("unchecked")
    public Result execute(String slug, String actionId, String encodedId, Map<String, String> parameters) {
        return executeInternal(slug, actionId, encodedId, parameters, false);
    }

    @SuppressWarnings("unchecked")
    private Result executeInternal(String slug, String actionId, String encodedId, Map<String, String> parameters, boolean inManagedTransaction) {
        var actor = actors.currentActor();
        if (actor == null || !permissions.canAccessAdmin(actor) || !permissions.canViewEntity(slug, actor)
                || !permissions.canExecuteAction(slug, actionId, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot execute this action.");
        }
        EntityDescriptor descriptor = metadata.getBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
        EntityAction<Object> action = (EntityAction<Object>) actions.getAction(descriptor.javaType(), actionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found"));
        // Copy input so a handler cannot rewrite the reason used for its audit record.
        Map<String, String> params = parameters == null ? new HashMap<>() : new HashMap<>(parameters);
        MutationInputs.validate(params);
        String reason = params.get("_reason");
        if ((reason != null && reason.length() > 1000) || (action.getRiskLevel() != RiskLevel.LOW && (reason == null || reason.isBlank()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a reason for this change (1–1000 characters).");
        }
        if (action.getExecutionMode() == io.github.yavonalabs.vectis.core.action.ActionExecutionMode.MANAGED_LOCAL && !inManagedTransaction) {
            return managed.commit(() -> executeInternal(slug, actionId, encodedId, params, true));
        }
        MutationReceipt receipt = null;
        if (inManagedTransaction) {
            if (!descriptor.hasVersion()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Managed actions require a versioned entity.");
            receipt = receipts.begin(actor.getName(), "ACTION:" + actionId, slug, encodedId, params);
            if (receipt.completed()) return new Result(action.getLabel(), Outcome.COMMITTED, true);
        }
        Object id;
        try { id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId()); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid record identifier."); }
        Object entity = queries.findById(descriptor, id);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
        if (descriptor.hasVersion()) {
            if (params.get("_version") == null || params.get("_version").isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review the current record before submitting this action; its version is required.");
            }
            Object version = PropertyAccessorFactory.forBeanPropertyAccess(entity).getPropertyValue(descriptor.versionField().name());
            if (!Objects.equals(String.valueOf(version), params.get("_version"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This record changed after the preview. Refresh and review the action again.");
            }
        }
        if (action.getHandler() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This action has no execution handler.");
        }
        var before = snapshot(entity, descriptor);
        if (inManagedTransaction && action.isRequiresConfirmation()) {
            proposals.consume(actor.getName(), slug, actionId, encodedId, params.get("_version"), params);
        }
        if (inManagedTransaction) managed.guardVersion(entity);
        try {
        action.getHandler().accept(entity, inManagedTransaction ? ActionProposalStore.businessInput(params) : java.util.Collections.unmodifiableMap(params));
        Object saved = queries.save(entity);
        var event = new VectisChangeEvent(this, slug, encodedId, action.getLabel(),
                VectisChangeEvent.OperationType.ACTION, before, snapshot(saved, descriptor), actor.getName(),
                reason == null ? "Executed via Vectis Console" : reason);
        if (inManagedTransaction) {
            event.withOperationId(params.get("_operation"));
            audit.recordMutation(event);
            receipt.complete(encodedId);
        } else {
            events.publishEvent(event);
        }
        return new Result(action.getLabel(), inManagedTransaction ? Outcome.COMMITTED : Outcome.HOST_COMPLETED, false);
        } catch (RuntimeException failure) {
            if (inManagedTransaction) throw failure;
            throw new HostActionOutcomeUnknown(failure);
        }
    }

    public static class HostActionOutcomeUnknown extends RuntimeException {
        HostActionOutcomeUnknown(Throwable cause) { super("Host action outcome is unconfirmed.", cause); }
    }

    private Map<String, Object> snapshot(Object entity, EntityDescriptor descriptor) {
        Map<String, Object> result = new HashMap<>();
        var wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (var field : descriptor.fields()) {
            Object value = wrapper.getPropertyValue(field.name());
            result.put(field.name(), value == null ? null : value.toString());
        }
        return result;
    }
}
