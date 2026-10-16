package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One stored version of a file artifact's content")
public record FileVersionResponse(
        UUID id,
        UUID artifactId,
        long seq,
        String mimeType,
        Long sizeBytes,
        String checksumSha256,
        UUID uploadedBy,
        @Schema(description = "Whether this is the version currently served for the artifact")
        boolean current,
        Instant createdAt
) {
}
