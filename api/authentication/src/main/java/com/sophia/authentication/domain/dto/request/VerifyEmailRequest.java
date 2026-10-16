package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Verify an email address with the emailed token")
public record VerifyEmailRequest(
        @NotBlank(message = "Token is required")
        String token) {
}
