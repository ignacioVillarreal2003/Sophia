package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Duplicates an artifact (recursively for folders). Defaults: same parent,
 * name suffixed with " (copy)".
 */
@Schema(description = "Duplicates an artifact, recursively for folders; defaults to the same parent "
        + "with the name suffixed \" (copy)\"")
public record DuplicateArtifactRequest(
        UUID targetParentId,

        @Size(min = 1, max = 255)
        String newName
) {
}
