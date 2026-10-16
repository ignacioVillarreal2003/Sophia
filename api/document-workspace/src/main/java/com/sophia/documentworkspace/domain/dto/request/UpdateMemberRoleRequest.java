package com.sophia.documentworkspace.domain.dto.request;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Updates a member's role within the workspace")
public record UpdateMemberRoleRequest(
        @NotNull
        WorkspaceRole role
) {
}
