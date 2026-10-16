package com.sophia.authentication.domain.dto.request;

import com.sophia.authentication.domain.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Directly set another user's password — no current password required")
public record AdminSetPasswordRequest(
        @NotBlank(message = "New password is required")
        @StrongPassword
        String newPassword) {
}
