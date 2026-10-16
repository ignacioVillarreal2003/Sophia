package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to create a new workspace")
public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 120)
        String name,

        @Size(max = 2000)
        String description,


        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex color like #6366F1")
        String color,


        @Size(min = 2, max = 10)
        String language
) {
}
