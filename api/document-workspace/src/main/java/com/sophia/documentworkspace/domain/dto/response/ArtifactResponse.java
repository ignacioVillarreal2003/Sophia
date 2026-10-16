package com.sophia.documentworkspace.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified artifact view. Type-specific fields are null for other types and
 * omitted from the JSON payload.
 */
@Schema(description = "Unified artifact view; type-specific fields (DOCUMENT and FILE below) are null and omitted from the JSON for types that don't use them")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArtifactResponse(
        UUID id,
        UUID workspaceId,
        UUID parentId,
        String name,
        ArtifactType type,
        UUID ownerId,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,

        // FOLDER
        String color,

        // DOCUMENT (the text itself is read through the content endpoint)
        Long contentVersion,

        // FILE
        String mimeType,
        Long sizeBytes,
        String originalFilename
) {
}
