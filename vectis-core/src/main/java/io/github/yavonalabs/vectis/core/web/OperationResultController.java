package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.mutation.*;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("${vectis.path:/admin}/api/operations")
public class OperationResultController {
    private final MutationActorProvider actors;
    private final MutationReceiptStore receipts;
    private final AdminPermissionEvaluator permissions;
    public OperationResultController(MutationActorProvider actors, MutationReceiptStore receipts, AdminPermissionEvaluator permissions) {
        this.actors = actors; this.receipts = receipts; this.permissions = permissions;
    }
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, String>> result(@PathVariable String key) {
        var actor = actors.currentActor();
        if (actor == null || !permissions.canAccessAdmin(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var receipt = receipts.findForActor(actor.getName(), key);
        if (receipt == null || receipt.entitySlug() == null || receipt.operation() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No recoverable result found for this account. Absence does not prove a host action had no effect.");
        String slug = receipt.entitySlug(), operation = receipt.operation();
        boolean allowed = permissions.canViewEntity(slug, actor) && (operation.startsWith("ACTION:")
                ? permissions.canExecuteAction(slug, operation.substring(7), actor)
                : operation.equals("DELETE") ? permissions.canDeleteEntity(slug, actor) : permissions.canEditEntity(slug, actor));
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("outcome", receipt.completed() ? "COMMITTED" : "UNKNOWN",
                "operationId", key, "entity", slug, "recordId", receipt.resultId() == null ? "" : receipt.resultId()));
    }
}
