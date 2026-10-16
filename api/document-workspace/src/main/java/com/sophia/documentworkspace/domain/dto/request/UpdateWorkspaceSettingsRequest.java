package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial preferences update: null fields keep their current value. */
@Schema(description = "Partial update of a workspace's preferences (PATCH); null fields keep their current value")
public record UpdateWorkspaceSettingsRequest(
        @Schema(description = "Days before trashed items are purged; 0 goes back to the system default")
        @Min(0) @Max(3650)
        Integer trashRetentionDays,
        @Schema(description = "Whether members may create public (unauthenticated) read-only links")
        Boolean publicSharingEnabled,
        @Schema(description = "IANA time zone, e.g. America/Montevideo")
        @Size(max = 50)
        String timezone,
        @Pattern(regexp = "^(yyyy-MM-dd|dd/MM/yyyy|MM/dd/yyyy)$", message = "dateFormat must be yyyy-MM-dd, dd/MM/yyyy or MM/dd/yyyy")
        String dateFormat
) {
}
