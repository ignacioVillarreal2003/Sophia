package com.sophia.authentication.domain.dto.request;

import com.sophia.authentication.domain.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Complete a password reset with the emailed token")
public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        @StrongPassword
        String newPassword) {
}
