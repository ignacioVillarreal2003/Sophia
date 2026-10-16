package com.sophia.authentication.domain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "sophia")
@Validated
public record ApplicationProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull OAuth2 oauth2,
        @Valid @NotNull Notifications notifications,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Monitoring monitoring,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Events events) {

    public record Security(
            @Valid @NotNull JsonWebToken jwt,
            @Valid @NotNull Cors cors,
            @Valid @NotNull Password password,
            @Valid @NotNull Lockout lockout,
            boolean requireEmailVerification,
            @NotNull Duration passwordResetTokenTtl,
            @NotNull Duration emailVerificationTokenTtl,
            @Valid @NotNull RateLimit rateLimit) {

        public record JsonWebToken(
                String privateKey,
                @NotBlank String issuer,
                @NotNull Duration accessTokenTtl,
                @NotNull Duration refreshTokenTtl,
                @NotNull Duration refreshTokenRememberMeTtl,
                @NotNull Duration serviceTokenTtl) {
        }

        public record Cors(
                @NotNull List<String> allowedOrigins,
                @NotNull List<String> allowedMethods,
                @NotNull List<String> allowedHeaders,
                @NotNull List<String> exposedHeaders,
                boolean allowCredentials,
                long maxAge) {
        }

        public record Password(int bcryptStrength, int historyCount) {
        }

        public record Lockout(
                int maxAttempts,
                @NotNull Duration lockDuration,
                @NotNull Duration attemptWindow) {
        }

        public record RateLimit(
                boolean enabled,
                int authenticationRequests,
                @NotNull Duration authenticationWindow,
                int generalRequests,
                @NotNull Duration generalWindow) {
        }
    }

    public record OAuth2(
            @NotNull List<String> authorizedRedirectUris,
            @NotNull Duration oneTimeCodeTtl) {
    }

    public record Notifications(
            boolean enabled,
            @NotBlank String commandsTopic,
            @NotNull Duration relayInterval,
            int relayBatchSize,
            @Min(1) int relayMaxAttempts) {
    }

    public record Storage(
            @NotBlank String bucket,
            String endpoint,
            @NotBlank String region,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess,
            @NotNull List<String> allowedImageTypes) {

        public boolean hasCustomEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }

        public boolean hasStaticCredentials() {
            return accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }

    public record Monitoring(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record Admin(
            boolean seedEnabled,
            @NotBlank String email,
            @NotBlank String password,
            @NotBlank String firstName,
            @NotBlank String lastName) {
    }

    public record Events(
            boolean enabled,
            @NotBlank String userTopic,
            @NotNull Duration relayInterval,
            int relayBatchSize,
            @Min(1) int relayMaxAttempts,
            int partitions,
            int replicas) {
    }
}
