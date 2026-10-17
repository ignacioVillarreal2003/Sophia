package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** What deleting a workspace takes with it, shown before the owner confirms. */
@Schema(description = "What would be lost if the workspace were deleted")
public record WorkspaceImpactResponse(
        long folders,
        long documents,
        long files,
        long usedBytes,
        long members,
        long activePublicLinks,
        long pendingAccessRequests
) {
}
