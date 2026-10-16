package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Creates a new artifact instance from a template")
public record InstantiateTemplateRequest(
        @NotBlank @Size(max = 255)
        String name,

        /** Destination folder of the new artifact; null for root. */
        @Schema(description = "Destination folder for the new artifact; omit for the workspace root")
        UUID parentId
) {
}
