package io.github.yavonalabs.vectis.core.mutation;

import io.github.yavonalabs.vectis.core.metadata.*;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import io.github.yavonalabs.vectis.core.event.VectisChangeEvent;
import io.github.yavonalabs.vectis.core.routing.IdCodec;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLogService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;

/** Managed CRUD: entity writes and success audit share the host default JPA transaction. */
public class RecordMutationService {
    private final EntityMetadataRegistry registry;
    private final DynamicCriteriaQueryEngine queryEngine;
    private final AdminPermissionEvaluator permissionEvaluator;
    private final MutationActorProvider actors;
    private final VectisAuditLogService auditLog;
    private final MutationReceiptStore receipts;
    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    public RecordMutationService(EntityMetadataRegistry registry, DynamicCriteriaQueryEngine queryEngine,
            AdminPermissionEvaluator permissionEvaluator, MutationActorProvider actors, VectisAuditLogService auditLog, MutationReceiptStore receipts) {
        this.registry = registry;
        this.queryEngine = queryEngine;
        this.permissionEvaluator = permissionEvaluator;
        this.actors = actors;
        this.auditLog = auditLog;
        this.receipts = receipts;
    }

    public static class InvalidRecord extends jakarta.validation.ConstraintViolationException {
        private final Object entity;
        public InvalidRecord(Object entity, jakarta.validation.ConstraintViolationException cause) {
            super(cause.getConstraintViolations());
            this.entity = entity;
        }
        public Object entity() { return entity; }
    }

    private Principal authorize(String slug, boolean delete) {
        Principal actor = actors.currentActor();
        if (actor == null || !permissionEvaluator.canAccessAdmin(actor) || !permissionEvaluator.canViewEntity(slug, actor)
                || !(delete ? permissionEvaluator.canDeleteEntity(slug, actor) : permissionEvaluator.canEditEntity(slug, actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        return actor;
    }

    private EntityDescriptor descriptor(String slug) {
        return registry.getBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    @Transactional(rollbackFor = Exception.class)
    public String save(String slug, Map<String, String> input) throws Exception {
        Principal principal = authorize(slug, false);
        EntityDescriptor descriptor = descriptor(slug);
        Map<String, String> formParams = input == null ? new HashMap<>() : new HashMap<>(input);
        requireReason(formParams);
        String rawId = formParams.get("__id");
        boolean isNew = rawId == null || rawId.isBlank();
        // Replay still requires current relationship permissions before returning a stored result.
        for (var association : descriptor.associations()) {
            if (formParams.containsKey(association.name())) {
                var target = registry.getByClass(association.targetEntityClass()).orElse(null);
                if (target == null || !permissionEvaluator.canViewEntity(target.slug(), principal)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change this relationship.");
                }
            }
        }
        var receipt = receipts.begin(principal.getName(), isNew ? "CREATE" : "UPDATE", slug, rawId, formParams);
        if (receipt.completed()) return receipt.resultId();
        Object entity = null;
        try {
            Map<String, Object> beforeSnapshot = new HashMap<>();

            if (isNew) {
                entity = descriptor.javaType().getDeclaredConstructor().newInstance();
            } else {
                Object id = decodeId(rawId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
                entity = queryEngine.findById(descriptor, id);
                if (entity == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + rawId);
                }
                beforeSnapshot = takeSnapshot(entity, descriptor);
            }

            BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);

            for (FieldDescriptor field : descriptor.fields()) {
                if (field.isId() && !isNew) continue;
                if (field.isVersion()) continue;

                String paramVal = formParams.get(field.name());
                if (paramVal != null) {
                    if (field.isBoolean()) {
                        wrapper.setPropertyValue(field.name(), Boolean.parseBoolean(paramVal));
                    } else if (paramVal.isBlank()) {
                        wrapper.setPropertyValue(field.name(), null);
                    } else {
                        wrapper.setPropertyValue(field.name(), conversionService.convert(paramVal, field.type()));
                    }
                } else if (field.isBoolean()) {
                    wrapper.setPropertyValue(field.name(), false);
                }
            }

            for (AssociationDescriptor assoc : descriptor.associations()) {
                if (assoc.isSingleValued()) {
                    EntityDescriptor target = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                    if (target == null || !permissionEvaluator.canViewEntity(target.slug(), principal)) {
                        if (formParams.containsKey(assoc.name())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change this relationship.");
                        }
                        continue;
                    }
                    String assocId = formParams.get(assoc.name());
                    if (assocId != null && !assocId.isBlank()) {
                        EntityDescriptor targetDesc = registry.getByClass(assoc.targetEntityClass()).orElse(null);
                        if (targetDesc != null && targetDesc.idField() != null) {
                            if (!permissionEvaluator.canViewEntity(targetDesc.slug(), principal)) {
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to related record.");
                            }
                            Object decodedTargetId = decodeId(assocId, targetDesc.idField().type(), targetDesc.idField().isEmbeddedId());
                            Object targetEntity = queryEngine.findById(targetDesc, decodedTargetId);
                            if (targetEntity == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Related record does not exist.");
                            wrapper.setPropertyValue(assoc.name(), targetEntity);
                        }
                    } else if (assocId != null) {
                        wrapper.setPropertyValue(assoc.name(), null);
                    }
                }
            }

            if (!isNew) requireVersion(descriptor, entity, formParams.get(descriptor.hasVersion() ? descriptor.versionField().name() : "_version"));
            Object savedEntity;
            if (isNew) {
                queryEngine.persist(entity);
                savedEntity = entity;
            } else {
                savedEntity = queryEngine.save(entity);
            }

            queryEngine.flush();
            Map<String, Object> afterSnapshot = takeSnapshot(savedEntity, descriptor);
            BeanWrapper savedWrapper = PropertyAccessorFactory.forBeanPropertyAccess(savedEntity);
            Object rawEntityId = descriptor.idField() != null ? savedWrapper.getPropertyValue(descriptor.idField().name()) : "N/A";
            String normalizedEncodedId = IdCodec.encode(rawEntityId, descriptor.idField() != null && descriptor.idField().isEmbeddedId());
            String username = principal.getName();

            // PERSIST DURABLE AUDIT EVENT WITH REASON
            auditLog.recordMutation(new VectisChangeEvent(
                    this,
                    slug,
                    normalizedEncodedId,
                    isNew ? "Create Record" : "Update Record",
                    isNew ? VectisChangeEvent.OperationType.CREATE : VectisChangeEvent.OperationType.UPDATE,
                    beforeSnapshot,
                    afterSnapshot,
                    username,
                    formParams.getOrDefault("_reason", "Standard operational edit")
            ).withOperationId(formParams.get("_operation")));

            receipt.complete(normalizedEncodedId);
            return normalizedEncodedId;
        } catch (jakarta.validation.ConstraintViolationException e) {
            throw new InvalidRecord(entity, e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String slug, String encodedId, Map<String, String> input) {
        Principal principal = authorize(slug, true);
        EntityDescriptor descriptor = descriptor(slug);
        Map<String, String> allParams = input == null ? new HashMap<>() : new HashMap<>(input);
        requireReason(allParams);
        var receipt = receipts.begin(principal.getName(), "DELETE", slug, encodedId, allParams);
        if (receipt.completed()) return;
        Object id = decodeId(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
        Object entity = queryEngine.findById(descriptor, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found: " + encodedId);
        }

        requireReason(allParams);
        requireVersion(descriptor, entity, allParams.get("_version"));
        Map<String, Object> beforeSnapshot = takeSnapshot(entity, descriptor);
        queryEngine.deleteById(descriptor, id);

        String username = principal.getName();
        String reason = (allParams != null) ? allParams.getOrDefault("_reason", "Deleted via Vectis Console") : "Deleted via Vectis Console";

        // PERSIST DURABLE AUDIT EVENT WITH REASON
        auditLog.recordMutation(new VectisChangeEvent(
                this,
                slug,
                encodedId,
                "Delete Record",
                VectisChangeEvent.OperationType.DELETE,
                beforeSnapshot,
                Map.of(),
                username,
                reason
        ).withOperationId(allParams.get("_operation")));
        receipt.complete(encodedId);

    }

    private void requireVersion(EntityDescriptor descriptor, Object entity, String supplied) {
        if (!descriptor.hasVersion()) return;
        if (supplied == null || supplied.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The reviewed record version is required. Reload and review the record.");
        }
        Object expected;
        try { expected = conversionService.convert(supplied, descriptor.versionField().type()); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid record version."); }
        Object current = PropertyAccessorFactory.forBeanPropertyAccess(entity).getPropertyValue(descriptor.versionField().name());
        if (!Objects.equals(current, expected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This record changed after it was reviewed. Reload and review the change again.");
        }
    }

    private Object decodeId(String value, Class<?> type, boolean embedded) {
        try { return IdCodec.decode(value, type, embedded); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid record identifier."); }
    }

    private void requireReason(Map<String, String> params) {
        String reason = params == null ? null : params.get("_reason");
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a reason for this change (1–1000 characters).");
        }
    }

    private Map<String, Object> takeSnapshot(Object entity, EntityDescriptor descriptor) {
        Map<String, Object> snapshot = new HashMap<>();
        if (entity == null) return snapshot;
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (FieldDescriptor field : descriptor.fields()) {
            try {
                Object val = wrapper.getPropertyValue(field.name());
                snapshot.put(field.name(), val != null ? val.toString() : null);
            } catch (Exception ignored) {}
        }
        return snapshot;
    }

}
