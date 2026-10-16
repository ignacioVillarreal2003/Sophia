package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Permission creation / update payload")
public record PermissionRequest(
        @Schema(example = "REPORT_EXPORT")
        @NotBlank(message = "Permission name is required")
        @Size(max = 100)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "Permission name must be upper snake case (e.g. REPORT_EXPORT)")
        String name,

        @Size(max = 255)
        String description,

        @Schema(example = "REPORTS")
        @Size(max = 50)
        String category) {
}
