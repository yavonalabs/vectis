package io.github.yavonalabs.vectis.core.security;

import java.security.Principal;

/** Safe fallback when the host has not supplied a security integration. */
public class DenyAllPermissionEvaluator implements AdminPermissionEvaluator {
    public boolean canAccessAdmin(Principal principal) { return false; }
    public boolean canViewEntity(String slug, Principal principal) { return false; }
    public boolean canEditEntity(String slug, Principal principal) { return false; }
    public boolean canDeleteEntity(String slug, Principal principal) { return false; }
    public boolean canExecuteAction(String slug, String action, Principal principal) { return false; }
    public boolean canViewAuditLogs(Principal principal) { return false; }
}
