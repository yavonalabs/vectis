package io.github.yavonalabs.vectis.core.security;

import io.github.yavonalabs.vectis.core.action.EntityAction;
import io.github.yavonalabs.vectis.core.action.EntityActionRegistry;
import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

public class SpringSecurityPermissionEvaluator implements AdminPermissionEvaluator {

    private final EntityMetadataRegistry metadataRegistry;
    private final EntityActionRegistry actionRegistry;
    private final ExpressionParser parser = new SpelExpressionParser();

    public SpringSecurityPermissionEvaluator(EntityMetadataRegistry metadataRegistry, EntityActionRegistry actionRegistry) {
        this.metadataRegistry = metadataRegistry;
        this.actionRegistry = actionRegistry;
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Override
    public boolean canAccessAdmin(Principal principal) {
        return getAuthentication() != null && getAuthentication().isAuthenticated();
    }

    @Override
    public boolean canViewEntity(String entitySlug, Principal principal) {
        return canAccessAdmin(principal);
    }

    @Override
    public boolean canEditEntity(String entitySlug, Principal principal) {
        return canAccessAdmin(principal);
    }

    @Override
    public boolean canDeleteEntity(String entitySlug, Principal principal) {
        return canAccessAdmin(principal);
    }

    @Override
    public boolean canExecuteAction(String entitySlug, String actionId, Principal principal) {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        EntityDescriptor descriptor = metadataRegistry.getBySlug(entitySlug).orElse(null);
        if (descriptor == null) return false;

        EntityAction<?> action = actionRegistry.getActionsForEntity(descriptor.javaType())
                .stream()
                .filter(a -> a.getId().equals(actionId))
                .findFirst()
                .orElse(null);

        if (action == null) return false;

        if (action.getRequiresPreAuthorize() != null && !action.getRequiresPreAuthorize().isBlank()) {
            return evaluateSpel(action.getRequiresPreAuthorize(), auth);
        }

        if (action.getRequiredRole() != null && !action.getRequiredRole().isBlank()) {
            return hasRole(auth, action.getRequiredRole());
        }

        return true;
    }

    @Override
    public boolean canViewAuditLogs(Principal principal) {
        return canAccessAdmin(principal);
    }

    private boolean hasRole(Authentication auth, String role) {
        String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority().equals(targetRole)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateSpel(String expressionStr, Authentication auth) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(new SecurityExpressionRoot(auth));
            Expression expression = parser.parseExpression(expressionStr);
            Boolean result = expression.getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            // Default to deny on SpEL error
            return false;
        }
    }

    // A minimal root object to support basic hasRole / hasAuthority in SpEL
    public static class SecurityExpressionRoot {
        public final Authentication authentication;

        public SecurityExpressionRoot(Authentication authentication) {
            this.authentication = authentication;
        }

        public boolean hasRole(String role) {
            String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            return hasAuthority(targetRole);
        }

        public boolean hasAnyRole(String... roles) {
            for (String role : roles) {
                if (hasRole(role)) return true;
            }
            return false;
        }

        public boolean hasAuthority(String authority) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if (auth.getAuthority().equals(authority)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasAnyAuthority(String... authorities) {
            for (String auth : authorities) {
                if (hasAuthority(auth)) return true;
            }
            return false;
        }
        
        public boolean permitAll() { return true; }
        public boolean denyAll() { return false; }
    }
}
