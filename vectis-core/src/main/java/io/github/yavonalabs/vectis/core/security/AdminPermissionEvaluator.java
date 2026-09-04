package io.github.yavonalabs.vectis.core.security;

import java.security.Principal;

public interface AdminPermissionEvaluator {
    boolean canAccessAdmin(Principal principal);
    boolean canViewEntity(String entitySlug, Principal principal);
    boolean canEditEntity(String entitySlug, Principal principal);
    boolean canDeleteEntity(String entitySlug, Principal principal);
    boolean canExecuteAction(String entitySlug, String actionId, Principal principal);
    boolean canViewAuditLogs(Principal principal);
}