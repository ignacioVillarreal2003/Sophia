package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Replace the role set of every listed user with the same set")
public record BulkUserRolesRequest(
        @NotEmpty(message = "At least one id is required")
        List<UUID> ids,

        @NotEmpty(message = "Roles are required")
        Set<String> roles) {
}
