package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Role creation / update payload")
public record RoleRequest(
        @Schema(example = "SUPPORT_AGENT")
        @NotBlank(message = "Role name is required")
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "Role name must be upper snake case (e.g. SUPPORT_AGENT)")
        String name,

        @Size(max = 255)
        String description,

        @Schema(description = "Permission names granted to this role", example = "[\"USER_READ\"]")
        Set<String> permissions) {
}
