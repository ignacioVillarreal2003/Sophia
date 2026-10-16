package com.sophia.documentworkspace.application.security;

import java.util.Set;
import java.util.UUID;

/**
 * Identity propagated by the authentication service inside the JWT.
 * This service never manages credentials; it only consumes verified claims.
 */
public record AuthenticatedUser(UUID id, String email, Set<String> globalRoles, Set<String> permissions) {

    /** Grants suspend/reactivate and other platform-wide workspace management —
     *  seeded onto the ADMIN role in api/authentication's db/seed migration. */
    public static final String PERMISSION_WORKSPACE_MANAGE = "WORKSPACE_MANAGE";

    public boolean hasGlobalRole(String role) {
        return globalRoles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
