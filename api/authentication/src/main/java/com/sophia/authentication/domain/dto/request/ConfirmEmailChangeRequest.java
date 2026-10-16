package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Confirm an email change with the token sent to the new address")
public record ConfirmEmailChangeRequest(
        @NotBlank(message = "Token is required")
        String token) {
}
