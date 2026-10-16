package com.sophia.authentication.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issued token pair plus the authenticated user profile")
public record AuthenticationResponse(
        @Schema(description = "JWT access token") String accessToken,
        @Schema(description = "Opaque refresh token; store it securely") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
        UserResponse user) {

    public static AuthenticationResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthenticationResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
