package com.sophia.notification.domain.config;

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
        @Valid @NotNull Commands commands,
        @Valid @NotNull UserEvents userEvents,
        @Valid @NotNull Email email,
        @Valid @NotNull Stream stream,
        @Valid @NotNull Frontend frontend,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Monitoring monitoring) {

    public record Security(@Valid @NotNull JsonWebToken jwt, @Valid @NotNull Cors cors,
                           @Valid @NotNull RateLimit rateLimit) {

        public record JsonWebToken(String jsonWebKeySetUri, String secret, String issuer) {

            public boolean hasJsonWebKeySetUri() {
                return jsonWebKeySetUri != null && !jsonWebKeySetUri.isBlank();
            }

            public boolean hasSecret() {
                return secret != null && !secret.isBlank();
            }
        }

        public record Cors(
                @NotNull List<String> allowedOrigins,
                @NotNull List<String> allowedMethods,
                @NotNull List<String> allowedHeaders,
                @NotNull List<String> exposedHeaders,
                boolean allowCredentials,
                long maxAge) {
        }

        public record RateLimit(boolean enabled, @Min(1) int requests, @NotNull Duration window) {
        }
    }

    public record Commands(
            @NotBlank String topic,
            @NotBlank String deadLetterTopic,
            @Min(1) int partitions,
            @Min(1) int replicas,
            @Min(1) int concurrency) {
    }

    public record UserEvents(
            @NotBlank String topic,
            @NotBlank String deadLetterTopic,
            @Min(1) int replicas,
            @Min(1) int concurrency) {
    }

    public record Email(
            boolean enabled,
            @NotBlank String from,
            @NotNull Duration dispatchInterval,
            @Min(1) int maxAttempts,
            @NotNull Duration baseBackoff,
            @NotNull Duration backlogRefreshInterval,
            @Min(1) int perRecipientLimit,
            @NotNull Duration perRecipientWindow) {
    }

    public record Stream(@NotNull Duration heartbeatInterval) {
    }

    public record Frontend(
            @NotBlank String baseUrl,
            @NotBlank String verifyEmailPath,
            @NotBlank String resetPasswordPath,
            @NotBlank String confirmEmailChangePath,
            @NotBlank String invitationPath,
            @NotBlank String notificationSettingsPath) {
    }

    public record Retention(
            @NotNull Duration readNotifications,
            @NotNull Duration notifications,
            @NotNull Duration completedEmails,
            @NotNull Duration processedMessages,
            @NotNull Duration auditLog) {
    }

    public record Monitoring(
            @NotBlank String username,
            @NotBlank String password) {
    }
}
