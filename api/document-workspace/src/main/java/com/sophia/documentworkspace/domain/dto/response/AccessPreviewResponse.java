package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** The little a signed-in non-member may see of a workspace: enough to ask for access. */
@Schema(description = "Name and color of a workspace, and the state of the caller's request to join it")
public record AccessPreviewResponse(
        UUID workspaceId,
        String name,
        String color,
        @Schema(description = "True when the caller already belongs to the workspace")
        boolean member,
        @Schema(description = "Status of the caller's latest request, or null when there is none")
        RequestStatus requestStatus,
        Instant requestedAt
) {
}
