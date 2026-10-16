package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Start an email change; a confirmation link is sent to the new address")
public record ChangeEmailRequest(
        @Schema(example = "new.address@example.com")
        @NotBlank(message = "New email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String newEmail,

        @NotBlank(message = "Password is required to change the email")
        String password) {
}
