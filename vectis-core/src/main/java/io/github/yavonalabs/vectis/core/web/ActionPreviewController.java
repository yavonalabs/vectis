package io.github.yavonalabs.vectis.core.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yavonalabs.vectis.core.action.EntityAction;
import io.github.yavonalabs.vectis.core.action.EntityActionRegistry;
import io.github.yavonalabs.vectis.core.annotation.RiskLevel;
import io.github.yavonalabs.vectis.core.context.DryRunContextHolder;
import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.routing.IdCodec;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/admin/api")
public class ActionPreviewController {

    private final Javers javers;
    private final ObjectMapper objectMapper;
    private final EntityActionRegistry actionRegistry;
    private final EntityMetadataRegistry descriptorRegistry;
    private final DynamicCriteriaQueryEngine queryEngine;
    private final jakarta.persistence.EntityManager entityManager;

    public ActionPreviewController(
            ObjectMapper objectMapper,
            EntityActionRegistry actionRegistry,
            EntityMetadataRegistry descriptorRegistry,
            DynamicCriteriaQueryEngine queryEngine,
            jakarta.persistence.EntityManager entityManager
    ) {
        this.javers = JaversBuilder.javers().build();
        this.objectMapper = objectMapper;
        this.actionRegistry = actionRegistry;
        this.descriptorRegistry = descriptorRegistry;
        this.queryEngine = queryEngine;
        this.entityManager = entityManager;
    }

    @PostMapping("/{slug}/action/{actionId}/{encodedId}/preview")
    @Transactional
    public ResponseEntity<?> previewAction(
            @PathVariable String slug,
            @PathVariable String actionId,
            @PathVariable String encodedId
    ) {
        try {
            EntityDescriptor descriptor = descriptorRegistry.getBySlug(slug)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            Optional<? extends EntityAction<?>> actionOpt = actionRegistry.getAction(descriptor.javaType(), actionId);
            if (actionOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found: " + actionId);
            }
            EntityAction<Object> action = (EntityAction<Object>) actionOpt.get();

            Object id = IdCodec.decode(encodedId, descriptor.idField().type(), descriptor.idField().isEmbeddedId());
            Object beforeState = queryEngine.findById(descriptor, id);
            if (beforeState == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            entityManager.detach(beforeState);

            Object managedEntity = queryEngine.findById(descriptor, id);
            if (managedEntity == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }

            // Set Dry Run Context
            DryRunContextHolder.set(true);

            try {
                if (action.getHandler() != null) {
                    action.getHandler().accept(managedEntity, Collections.emptyMap());
                }
            } finally {
                DryRunContextHolder.clear();
            }

            // Compute Diff
            Diff diff = javers.compare(beforeState, managedEntity);
            RiskLevel riskLevel = action.getRiskLevel();

            List<String> plainTextChanges = new ArrayList<>();
            for (org.javers.core.diff.Change change : diff.getChanges()) {
                if (change instanceof org.javers.core.diff.changetype.PropertyChange propertyChange) {
                    String prop = propertyChange.getPropertyName();
                    io.github.yavonalabs.vectis.core.metadata.FieldDescriptor fieldDesc = descriptor.fields().stream()
                            .filter(f -> f.name().equals(prop))
                            .findFirst().orElse(null);
                    
                    String displayName = fieldDesc != null ? fieldDesc.displayName() : prop;

                    if (change instanceof org.javers.core.diff.changetype.ValueChange vc) {
                        String leftStr = formatValue(vc.getLeft(), fieldDesc);
                        String rightStr = formatValue(vc.getRight(), fieldDesc);
                        plainTextChanges.add(displayName + " changed from " + leftStr + " to " + rightStr);
                    } else if (change instanceof org.javers.core.diff.changetype.ReferenceChange) {
                        plainTextChanges.add(displayName + " reference was updated");
                    } else if (change instanceof org.javers.core.diff.changetype.container.ContainerChange) {
                        plainTextChanges.add(displayName + " collection was modified");
                    } else {
                        plainTextChanges.add(displayName + " was changed");
                    }
                } else if (change instanceof org.javers.core.diff.changetype.NewObject) {
                    plainTextChanges.add("Created new object");
                } else if (change instanceof org.javers.core.diff.changetype.ObjectRemoved) {
                    plainTextChanges.add("Removed object");
                }
            }

            Map<String, Object> responseBody = Map.of(
                    "riskLevel", riskLevel.name(),
                    "hasChanges", !diff.getChanges().isEmpty(),
                    "plainTextChanges", plainTextChanges
            );

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(responseBody));

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new RuntimeException("Preview failed: " + e.getMessage(), e);
        } finally {
            if (!TransactionAspectSupport.currentTransactionStatus().isRollbackOnly()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
        }
    }

    private String formatValue(Object val, io.github.yavonalabs.vectis.core.metadata.FieldDescriptor fieldDesc) {
        if (val == null) return "null";
        if (fieldDesc != null && fieldDesc.isDecimal() && val instanceof Number n) {
            java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
            format.setMinimumFractionDigits(2);
            format.setMaximumFractionDigits(2);
            return "$" + format.format(n);
        }
        return val.toString();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handlePreviewException(Exception ex) {
        // If it's a ResponseStatusException (e.g. 404), respect its status
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getMessage();
        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason();
        } else if (ex.getCause() instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason();
        }
        
        return ResponseEntity.status(status)
                .header("Content-Type", "application/json")
                .body(Map.of("error", message != null ? message : "An error occurred during preview generation."));
    }
}
