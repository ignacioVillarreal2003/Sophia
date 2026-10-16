package com.sophia.authentication.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth2 token response (RFC 6749) for the client_credentials grant")
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @Schema(description = "Space-separated scopes granted to the token") String scope) {

    public static TokenResponse bearer(String accessToken, long expiresIn, String scope) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, scope);
    }
}
