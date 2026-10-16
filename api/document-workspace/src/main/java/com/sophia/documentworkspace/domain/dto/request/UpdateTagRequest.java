package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** PATCH semantics; {@code parentTagId} null detaches the tag from its parent. */
@Schema(description = "Partial tag update (PATCH); null fields keep their current value")
public record UpdateTagRequest(
        @Size(min = 1, max = 80)
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex color like #6366F1")
        String color,

        @Schema(description = "Null detaches the tag from its parent")
        UUID parentTagId
) {
}
