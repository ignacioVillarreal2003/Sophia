package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH semantics: null fields keep their current value; an empty description or color clears it. */
@Schema(description = "Partial artifact update (PATCH); null fields keep their current value")
public record UpdateArtifactRequest(
        @Size(min = 1, max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "color must be a hex color like #6366F1")
        String color
) {
}
