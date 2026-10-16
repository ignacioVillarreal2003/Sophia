package com.sophia.documentworkspace.domain.dto.request;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceStatusRequest(@NotNull WorkspaceStatus status) {
}
