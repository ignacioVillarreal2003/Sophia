package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One saved snapshot in a document history")
public record DocumentVersionResponse(
        UUID id,
        UUID documentId,
        long version,
        @Schema(description = "Set when the snapshot was named as a checkpoint")
        String name,
        UUID createdBy,
        Instant savedAt,
        int sizeChars,
        @Schema(description = "Only present when a single version is requested")
        String content
) {
}
