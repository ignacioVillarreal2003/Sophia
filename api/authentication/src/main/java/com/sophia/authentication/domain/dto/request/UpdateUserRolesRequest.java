package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

@Schema(description = "Replace the user's role set")
public record UpdateUserRolesRequest(
        @Schema(example = "[\"USER\"]")
        @NotNull(message = "Roles are required")
        Set<String> roles) {
}
