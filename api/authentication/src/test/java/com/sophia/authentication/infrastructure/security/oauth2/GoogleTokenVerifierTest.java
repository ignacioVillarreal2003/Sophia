package com.sophia.authentication.infrastructure.security.oauth2;

import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "google-client-id";

    private Jwt googleToken(Consumer<Jwt.Builder> customizer) {
        Jwt.Builder builder = Jwt.withTokenValue("raw-token")
                .header("alg", "RS256")
                .subject("google-subject-1")
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("email", "jane@test.local")
                .claim("email_verified", true)
                .claim("given_name", "Jane")
                .claim("family_name", "Doe")
                .claim("picture", "https://img.test/jane.png");
        customizer.accept(builder);
        return builder.build();
    }

    private GoogleTokenVerifier verifierReturning(Jwt token) {
        return new GoogleTokenVerifier(CLIENT_ID, tokenValue -> token);
    }

    @Test
    @DisplayName("maps the Google token claims into GoogleUserInfo")
    void verifyMapsClaims() {
        GoogleUserInfo info = verifierReturning(googleToken(builder -> {
        })).verify("raw-token");

        assertThat(info.subject()).isEqualTo("google-subject-1");
        assertThat(info.email()).isEqualTo("jane@test.local");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.firstName()).isEqualTo("Jane");
        assertThat(info.lastName()).isEqualTo("Doe");
        assertThat(info.pictureUrl()).isEqualTo("https://img.test/jane.png");
    }

    @Test
    @DisplayName("falls back to the email when the name claims are missing")
    void verifyDefaultsMissingNames() {
        GoogleUserInfo info = verifierReturning(googleToken(builder -> builder
                .claim("given_name", null)
                .claim("family_name", null)))
                .verify("raw-token");

        assertThat(info.firstName()).isEqualTo("jane@test.local");
        assertThat(info.lastName()).isEmpty();
    }

    @Test
    @DisplayName("rejects tokens without an email claim")
    void verifyRejectsMissingEmail() {
        GoogleTokenVerifier verifier = verifierReturning(googleToken(builder -> builder.claim("email", null)));

        assertThatThrownBy(() -> verifier.verify("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("translates a bad JWT into an invalid token error")
    void verifyTranslatesBadJwt() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier(CLIENT_ID, tokenValue -> {
            throw new BadJwtException("broken");
        });

        assertThatThrownBy(() -> verifier.verify("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("translates infrastructure failures into service unavailable")
    void verifyTranslatesInfrastructureFailure() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier(CLIENT_ID, (JwtDecoder) tokenValue -> {
            throw new JwtException("jwks unreachable");
        });

        assertThatThrownBy(() -> verifier.verify("raw-token"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("accepts Google issuers and rejects anything else")
    void issuerValidation() {
        GoogleTokenVerifier verifier = verifierReturning(googleToken(builder -> {
        }));

        OAuth2TokenValidatorResult trusted = verifier.issuerValidator()
                .validate(googleToken(builder -> {
                }));
        OAuth2TokenValidatorResult untrusted = verifier.issuerValidator()
                .validate(googleToken(builder -> builder.issuer("https://evil.test")));

        assertThat(trusted.hasErrors()).isFalse();
        assertThat(untrusted.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("accepts only tokens whose audience contains the configured client id")
    void audienceValidation() {
        GoogleTokenVerifier verifier = verifierReturning(googleToken(builder -> {
        }));

        OAuth2TokenValidatorResult matching = verifier.audienceValidator()
                .validate(googleToken(builder -> {
                }));
        OAuth2TokenValidatorResult mismatching = verifier.audienceValidator()
                .validate(googleToken(builder -> builder.audience(List.of("someone-else"))));

        assertThat(matching.hasErrors()).isFalse();
        assertThat(mismatching.hasErrors()).isTrue();
    }
}
