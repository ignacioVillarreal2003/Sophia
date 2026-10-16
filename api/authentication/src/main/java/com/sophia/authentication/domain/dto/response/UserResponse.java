package com.sophia.authentication.domain.dto.response;

import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import com.sophia.authentication.domain.entity.enums.Theme;
import com.sophia.authentication.domain.entity.enums.ThemeMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "User profile")
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String photoUrl,
        boolean active,
        boolean emailVerified,
        AuthenticationProvider authenticationProvider,
        @Schema(description = "Whether the account has a password set (false for Google-only accounts "
                + "that never went through the reset-password flow)")
        boolean hasPassword,
        @Schema(description = "Whether a Google account is currently linked — independent of "
                + "authenticationProvider, which reflects only how the account was first created")
        boolean googleLinked,
        @Schema(description = "The linked Google account's own email, independent of this "
                + "user's login email; null when no Google account is linked")
        String googleEmail,
        Instant lastLoginAt,
        Set<String> roles,
        @Schema(description = "Effective permissions (role-derived plus direct grants)")
        Set<String> permissions,
        @Schema(description = "Permissions granted directly to the user, independent of roles")
        Set<String> directPermissions,
        Theme theme,
        ThemeMode themeMode,
        String locale,
        String timezone,
        @Schema(description = "Module slugs the user has hidden from the home screen; "
                + "everything not listed here is shown")
        List<String> hiddenModules,
        Instant createdAt,
        Instant updatedAt) {
}
