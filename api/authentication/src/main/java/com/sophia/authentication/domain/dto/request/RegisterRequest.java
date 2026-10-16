package com.sophia.authentication.domain.dto.request;

import com.sophia.authentication.domain.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to register a new local account")
public record RegisterRequest(
        @Schema(example = "Jane", description = "Optional; can be completed later from the profile")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Schema(example = "Doe", description = "Optional; can be completed later from the profile")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Schema(example = "jane.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Schema(example = "S3cure!Password")
        @NotBlank(message = "Password is required")
        @StrongPassword
        String password) {
}
