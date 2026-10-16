package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request a password reset email")
public record ForgotPasswordRequest(
        @Schema(example = "jane.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email) {
}
