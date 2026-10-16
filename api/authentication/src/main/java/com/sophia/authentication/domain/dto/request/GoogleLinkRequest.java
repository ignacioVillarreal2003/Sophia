package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload to link a Google account to the authenticated user")
public record GoogleLinkRequest(
        @Schema(description = "Google ID token of the account to link")
        @NotBlank(message = "Google ID token is required")
        String idToken) {
}
