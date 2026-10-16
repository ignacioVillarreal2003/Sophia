package com.sophia.documentworkspace.domain.dto.request;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Creates a non-file artifact: a folder, a Markdown document, a checklist or a spreadsheet. File
 * artifacts are created through the upload flow. {@code content} is the initial Markdown (or workbook
 * JSON for spreadsheets) and is optional; {@code color} only applies to folders.
 */
@Schema(description = "Creates a folder, Markdown document, checklist or spreadsheet; "
        + "file artifacts are created through the upload flow instead")
public record CreateArtifactRequest(
        @NotNull
        ArtifactType type,

        @NotBlank @Size(max = 255)
        String name,

        UUID parentId,

        @Size(max = 2000)
        String description,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex color like #6366F1")
        String color,

        @Size(max = 8_000_000)
        String content
) {
}
