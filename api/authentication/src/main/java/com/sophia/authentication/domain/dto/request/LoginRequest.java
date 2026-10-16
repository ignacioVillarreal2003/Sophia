package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials login payload")
public record LoginRequest(
        @Schema(example = "jane.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @Schema(example = "S3cure!Password")
        @NotBlank(message = "Password is required")
        String password,

        @Schema(description = "Extends the refresh token lifetime", example = "false")
        boolean rememberMe,

        @Schema(description = "Optional friendly device name shown in the session list", example = "Chrome on Windows")
        @Size(max = 255)
        String deviceName) {
}
