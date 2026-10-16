package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Google Sign-In payload. The ID token is verified against Google's JSON Web Key Set and never stored.")
public record GoogleLoginRequest(
        @Schema(description = "Google ID token obtained by the client (Google Identity Services)")
        @NotBlank(message = "Google ID token is required")
        String idToken,

        @Schema(description = "Extends the refresh token lifetime", example = "false")
        boolean rememberMe,

        @Schema(description = "Optional friendly device name", example = "Pixel 9")
        @Size(max = 255)
        String deviceName) {
}
