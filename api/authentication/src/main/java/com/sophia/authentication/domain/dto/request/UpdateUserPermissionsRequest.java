package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

@Schema(description = "Replace the user's direct permission grants")
public record UpdateUserPermissionsRequest(
        @Schema(example = "[\"USER_READ\", \"AUDIT_READ\"]")
        @NotNull(message = "Permissions are required")
        Set<String> permissions) {
}
