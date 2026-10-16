package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Storage used by a workspace's files against its optional quota")
public record WorkspaceUsageResponse(
        long usedBytes,
        @Schema(description = "Quota in megabytes, or null when the workspace has no limit")
        Long quotaMb
) {
}
