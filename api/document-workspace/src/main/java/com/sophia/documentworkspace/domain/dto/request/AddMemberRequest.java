package com.sophia.documentworkspace.domain.dto.request;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Adds an existing platform user to the workspace (workspace ADMINs only)")
public record AddMemberRequest(
        @NotBlank @Email @Size(max = 320)
        String email,

        @NotNull
        WorkspaceRole role
) {
}
