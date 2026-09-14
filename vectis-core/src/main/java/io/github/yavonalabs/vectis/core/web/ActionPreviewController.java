package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.action.EntityAction;
import io.github.yavonalabs.vectis.core.action.EntityActionRegistry;
import io.github.yavonalabs.vectis.core.context.DryRunContextHolder;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.routing.IdCodec;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("${vectis.path:/admin}/api")
public class ActionPreviewController {
    private final EntityActionRegistry actions;
    private final EntityMetadataRegistry registry;
    private final DynamicCriteriaQueryEngine queries;
    private final AdminPermissionEvaluator permissions;
    private final Validator validator;

    public ActionPreviewController(EntityActionRegistry actions, EntityMetadataRegistry registry,
            DynamicCriteriaQueryEngine queries, AdminPermissionEvaluator permissions, Validator validator) {
        this.actions = actions;
        this.registry = registry;
        this.queries = queries;
        this.permissions = permissions;
        this.validator = validator;
    }

    @PostMapping("/{slug}/action/{actionId}/{encodedId}/preview")
    @SuppressWarnings("unchecked")
    public Map<String, Object> previewAction(@PathVariable String slug, @PathVariable String actionId,
            @PathVariable String encodedId, @RequestParam Map<String, String> params, Principal principal) throws Exception {
        if (!permissions.canAccessAdmin(principal) || !permissions.canViewEntity(slug, principal)
                || !permissions.canExecuteAction(slug, actionId, principal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot preview this action.");
        }
        var descriptor = registry.getBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        EntityAction<Object> action = (EntityAction<Object>) actions.getAction(descriptor.javaType(), actionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (action.getPreviewHandler() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Preview unavailable for this action. Ask your application administrator to configure a preview.");
        }
        Object id;
        try { id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId()); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid record identifier."); }
        Object original = queries.findById(descriptor, id);
        if (original == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found.");
        Object candidate = descriptor.javaType().getDeclaredConstructor().newInstance();
        BeanUtils.copyProperties(original, candidate);
        Map<String, Object> proposed;
        DryRunContextHolder.set(true);
        try { proposed = action.getPreviewHandler().apply(candidate, Collections.unmodifiableMap(params)); }
        finally { DryRunContextHolder.clear(); }
        if (proposed == null) throw new IllegalStateException("Preview must return proposed values.");
        var before = PropertyAccessorFactory.forBeanPropertyAccess(original);
        // Accept only explicitly returned visible scalar values, not arbitrary object diffs.
        BeanUtils.copyProperties(original, candidate);
        var after = PropertyAccessorFactory.forBeanPropertyAccess(candidate);
        List<String> changes = new ArrayList<>();
        for (var field : descriptor.fields()) {
            if (field.isId() || field.isVersion() || !proposed.containsKey(field.name())) continue;
            Object value = proposed.get(field.name());
            after.setPropertyValue(field.name(), value);
            if (!Objects.equals(before.getPropertyValue(field.name()), value)) {
                changes.add(field.displayName() + " changed from " + field.format(before.getPropertyValue(field.name())) + " to " + field.format(value));
            }
        }
        if (!validator.validate(candidate).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This action would produce invalid data. Review the action's business rules before continuing.");
        }
        return Map.of("riskLevel", action.getRiskLevel().name(), "hasChanges", !changes.isEmpty(),
                "plainTextChanges", changes, "version", descriptor.hasVersion() ? String.valueOf(before.getPropertyValue(descriptor.versionField().name())) : "");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        if (ex instanceof ResponseStatusException status) {
            return ResponseEntity.status(status.getStatusCode()).body(Map.of("error", Objects.requireNonNullElse(status.getReason(), "Request failed.")));
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "Preview could not be generated. No action was executed."));
    }
}
