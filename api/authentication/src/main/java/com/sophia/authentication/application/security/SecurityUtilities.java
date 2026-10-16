package com.sophia.authentication.application.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtilities {

    private SecurityUtilities() {
    }

    public static Optional<CustomUserDetails> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return Optional.of(details);
        }
        return Optional.empty();
    }

    public static Optional<UUID> getCurrentUserId() {
        return currentUser().map(CustomUserDetails::getId);
    }

    public static Optional<String> getCurrentUserEmail() {
        return currentUser().map(CustomUserDetails::getEmail);
    }

    public static Optional<UUID> getCurrentSessionId() {
        return currentUser().map(CustomUserDetails::getSessionId);
    }
}
