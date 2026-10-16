package com.sophia.authentication.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Registered machine-to-machine client")
public record ServiceClientResponse(
        @Schema(description = "Client id used in the client_credentials grant") UUID id,
        String name,
        String description,
        List<String> scopes,
        boolean active,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Plain client secret; returned ONLY on creation and rotation — store it now")
        String clientSecret) {
}
