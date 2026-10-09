package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.mutation.MutationActorProvider;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("${vectis.path:/admin}/api/build")
public class BuildIdentityController {
    private final MutationActorProvider actors;
    private final AdminPermissionEvaluator permissions;
    public BuildIdentityController(MutationActorProvider actors, AdminPermissionEvaluator permissions) { this.actors = actors; this.permissions = permissions; }
    @GetMapping
    public ResponseEntity<Map<String, String>> build() {
        var actor = actors.currentActor();
        if (actor == null || !permissions.canAccessAdmin(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/META-INF/vectis-build.properties")) {
            if (stream != null) properties.load(stream);
        } catch (java.io.IOException ignored) { /* Missing metadata is explicitly unknown. */ }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "version", properties.getProperty("version", "unknown"), "revision", properties.getProperty("revision", "unknown")));
    }
}
