package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Creates a tag within a workspace, optionally nested under a parent tag")
public record CreateTagRequest(
        @NotBlank @Size(max = 80)
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex color like #6366F1")
        String color,

        UUID parentTagId
) {
}
