package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Activate or deactivate a set of users in one request")
public record BulkUserStatusRequest(
        @NotEmpty(message = "At least one id is required")
        List<UUID> ids,

        @NotNull(message = "Active flag is required")
        Boolean active) {
}
