package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Service client creation / update payload")
public record ServiceClientRequest(
        @Schema(example = "billing-service")
        @NotBlank(message = "Name is required")
        @Size(max = 100)
        String name,

        @Size(max = 255)
        String description,

        @Schema(description = "Scopes granted to the client; each must be an existing permission",
                example = "[\"USER_READ\"]")
        @NotNull(message = "Scopes are required")
        Set<String> scopes,

        @Schema(description = "Whether the client can request tokens", example = "true")
        Boolean active) {
}
