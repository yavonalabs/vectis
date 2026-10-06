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
 * Legacy action orchestration boundary. Intentionally not transactional: existing
 * handlers may own transactions or external effects. Atomic managed mode follows separately.
 */
public class ActionMutationService {
    private final EntityMetadataRegistry metadata;
    private final EntityActionRegistry actions;
    private final DynamicCriteriaQueryEngine queries;
    private final AdminPermissionEvaluator permissions;
    private final MutationActorProvider actors;
    private final ApplicationEventPublisher events;

    public ActionMutationService(EntityMetadataRegistry metadata, EntityActionRegistry actions,
            DynamicCriteriaQueryEngine queries, AdminPermissionEvaluator permissions,
            MutationActorProvider actors, ApplicationEventPublisher events) {
        this.metadata = metadata;
        this.actions = actions;
        this.queries = queries;
        this.permissions = permissions;
        this.actors = actors;
        this.events = events;
    }

    public record Result(String actionLabel) {}

    @SuppressWarnings("unchecked")
    public Result execute(String slug, String actionId, String encodedId, Map<String, String> parameters) {
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
        String reason = params.get("_reason");
        if (action.getRiskLevel() != RiskLevel.LOW && (reason == null || reason.isBlank() || reason.length() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a reason for this change (1–1000 characters).");
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
        action.getHandler().accept(entity, params);
        Object saved = queries.save(entity);
        events.publishEvent(new VectisChangeEvent(this, slug, encodedId, action.getLabel(),
                VectisChangeEvent.OperationType.ACTION, before, snapshot(saved, descriptor), actor.getName(),
                reason == null ? "Executed via Vectis Console" : reason));
        return new Result(action.getLabel());
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
