package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Add a set of direct permissions to every listed user, on top of what each already has")
public record BulkUserPermissionsRequest(
        @NotEmpty(message = "At least one id is required")
        List<UUID> ids,

        @NotEmpty(message = "Permissions are required")
        Set<String> permissions) {
}
