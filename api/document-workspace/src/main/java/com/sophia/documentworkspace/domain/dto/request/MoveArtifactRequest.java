package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Moves an artifact under a new parent folder; null means workspace root. */
@Schema(description = "Moves an artifact under a new parent folder")
public record MoveArtifactRequest(
        @Schema(description = "Null moves the artifact to the workspace root")
        UUID newParentId
) {
}
