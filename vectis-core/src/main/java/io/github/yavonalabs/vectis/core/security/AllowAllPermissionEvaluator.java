package io.github.yavonalabs.vectis.core.security;

import java.security.Principal;

public class AllowAllPermissionEvaluator implements AdminPermissionEvaluator {

    @Override
    public boolean canAccessAdmin(Principal principal) {
        return true;
    }

    @Override
    public boolean canViewEntity(String entitySlug, Principal principal) {
        return true;
    }

    @Override
    public boolean canEditEntity(String entitySlug, Principal principal) {
        return true;
    }

    @Override
    public boolean canDeleteEntity(String entitySlug, Principal principal) {
        return true;
    }

    @Override
    public boolean canExecuteAction(String entitySlug, String actionId, Principal principal) {
        return true;
    }

    @Override
    public boolean canViewAuditLogs(Principal principal) {
        return true;
    }
}