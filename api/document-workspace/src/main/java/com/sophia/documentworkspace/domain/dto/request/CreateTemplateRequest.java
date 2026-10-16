package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Creates a template by snapshotting an existing artifact's structure. */
@Schema(description = "Creates a reusable template by snapshotting an existing artifact's structure")
public record CreateTemplateRequest(
        @NotNull
        UUID sourceArtifactId,

        @NotBlank @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description,

        /** Folder where the template artifact itself lives; null for root. */
        @Schema(description = "Folder where the template artifact itself will live; omit for the workspace root")
        UUID parentId
) {
}
