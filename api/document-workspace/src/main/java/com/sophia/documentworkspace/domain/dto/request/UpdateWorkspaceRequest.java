package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH semantics: null fields keep their current value. */
@Schema(description = "Partial workspace update (PATCH); null fields keep their current value")
public record UpdateWorkspaceRequest(
        @Size(min = 1, max = 120)
        String name,

        @Size(max = 2000)
        String description,


        @Pattern(regexp = "^(#[0-9A-Fa-f]{6}|[a-z][a-z0-9-]{0,15})$", message = "color must be a hex color like #6366F1 or the id of a pattern")
        String color,


        @Size(min = 2, max = 10)
        String language
) {
}
