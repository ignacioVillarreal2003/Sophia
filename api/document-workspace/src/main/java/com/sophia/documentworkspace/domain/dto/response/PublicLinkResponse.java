package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A public link; the raw token is only present in the response to its creation")
public record PublicLinkResponse(
        UUID id,
        UUID artifactId,
        UUID createdBy,
        Instant createdAt,
        Instant expiresAt,
        @Schema(description = "Shown once, right after creation; never returned again")
        String token
) {
}
