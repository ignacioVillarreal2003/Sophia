package com.sophia.notification.application.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Set<String> globalRoles, Set<String> permissions) {

    public boolean hasGlobalRole(String role) {
        return globalRoles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
