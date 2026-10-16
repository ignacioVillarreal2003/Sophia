package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Activate or deactivate a user")
public record UpdateUserStatusRequest(
        @NotNull(message = "Active flag is required")
        Boolean active) {
}
