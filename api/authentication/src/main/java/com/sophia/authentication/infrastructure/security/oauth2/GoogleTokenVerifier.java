package com.sophia.authentication.infrastructure.security.oauth2;

import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_JSON_WEB_KEY_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> GOOGLE_ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId) {
        this.clientId = clientId;
    }

    GoogleTokenVerifier(String clientId, JwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
    }

    @Retry(name = "google")
    @CircuitBreaker(name = "google")
    public GoogleUserInfo verify(String idToken) {
        try {
            Jwt jsonWebToken = getDecoder().decode(idToken);
            String email = jsonWebToken.getClaimAsString("email");
            if (!StringUtils.hasText(email)) {
                throw new InvalidTokenException("Google token does not contain an email address");
            }
            return new GoogleUserInfo(
                    jsonWebToken.getSubject(),
                    email,
                    Boolean.TRUE.equals(jsonWebToken.getClaimAsBoolean("email_verified")),
                    StringUtils.hasText(jsonWebToken.getClaimAsString("given_name")) ? jsonWebToken.getClaimAsString("given_name") : email,
                    StringUtils.hasText(jsonWebToken.getClaimAsString("family_name")) ? jsonWebToken.getClaimAsString("family_name") : "",
                    jsonWebToken.getClaimAsString("picture"));
        } catch (BadJwtException e) {
            throw new InvalidTokenException("Invalid Google ID token");
        } catch (JwtException e) {
            throw new ServiceUnavailableException("Google token verification is temporarily unavailable");
        }
    }

    private JwtDecoder getDecoder() {
        JwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(Duration.ofSeconds(2));
                    requestFactory.setReadTimeout(Duration.ofSeconds(3));
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JSON_WEB_KEY_SET_URI)
                            .restOperations(new RestTemplate(requestFactory))
                            .build();
                    nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            issuerValidator(),
                            audienceValidator()));
                    decoder = local = nimbus;
                }
            }
        }
        return local;
    }

    OAuth2TokenValidator<Jwt> issuerValidator() {
        return jsonWebToken -> GOOGLE_ISSUERS.contains(String.valueOf(jsonWebToken.getIssuer()))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_issuer", "Untrusted issuer", null));
    }

    OAuth2TokenValidator<Jwt> audienceValidator() {
        return jsonWebToken -> jsonWebToken.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience", "Audience mismatch", null));
    }
}
