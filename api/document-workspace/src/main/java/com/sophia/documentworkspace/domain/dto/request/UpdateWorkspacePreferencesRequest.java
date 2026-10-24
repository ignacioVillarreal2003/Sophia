package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Choices about the caller's own workspaces")
public record UpdateWorkspacePreferencesRequest(
        @Schema(description = "Days a workspace the caller deleted stays in the trash before it is erased for good")
        @Min(1) @Max(365) int trashRetentionDays
) {
}
