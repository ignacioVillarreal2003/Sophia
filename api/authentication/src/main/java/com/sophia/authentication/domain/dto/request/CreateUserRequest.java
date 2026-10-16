package com.sophia.authentication.domain.dto.request;

import com.sophia.authentication.domain.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Administrative user creation payload")
public record CreateUserRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @StrongPassword
        String password,

        @Schema(description = "Role names to assign", example = "[\"USER\"]")
        Set<String> roles,

        @Schema(description = "Whether the account starts active", example = "true")
        Boolean active,

        @Schema(description = "Mark the email as already verified", example = "false")
        Boolean emailVerified) {
}
