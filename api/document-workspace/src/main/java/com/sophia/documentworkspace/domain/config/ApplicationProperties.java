package com.sophia.documentworkspace.domain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "sophia")
@Validated
public record ApplicationProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Search search,
        @Valid @NotNull Events events,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Realtime realtime,
        @Valid @NotNull Uploads uploads,
        @Valid @NotNull Monitoring monitoring
) {

    public record Security(@Valid @NotNull JsonWebToken jwt, @Valid @NotNull Cors cors, @Valid @NotNull RateLimit rateLimit) {

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

    public record Storage(
            @NotBlank String bucket,
            String endpoint,
            @NotBlank String region,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess,
            @NotNull Duration presignedUrlTtl,
            @NotNull DataSize multipartPartSize
    ) {
        public boolean hasStaticCredentials() {
            return accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }

        public boolean hasCustomEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }
    }

    public record Search(
            @NotBlank String host,
            @Min(1) int port,
            @NotBlank String scheme,
            String username,
            String password,
            @NotBlank String indexPrefix
    ) {
        public boolean hasCredentials() {
            return username != null && !username.isBlank();
        }
    }

    public record Events(
            @NotBlank String topic,
            @NotBlank @DefaultValue("sophia.notifications.commands") String notificationCommandsTopic,
            @NotNull Duration relayInterval,
            @Min(1) int relayBatchSize,
            @Min(1) @DefaultValue("10") int relayMaxAttempts
    ) {
    }

    public record Retention(
            @Min(1) int trashRetentionDays,
            /** Days a deleted workspace stays in the trash before it is erased for good. */
            @Min(1) @DefaultValue("30") int workspaceTrashDays,
            /** The owner is warned this many days before a trashed workspace is erased. */
            @Min(1) @DefaultValue("3") int workspacePurgeWarningDays) {
    }

    public record Realtime(@NotNull List<String> allowedOrigins) {
    }

    public record Uploads(@NotNull DataSize maxFileSize) {
    }

    public record Monitoring(
            @NotBlank String username,
            @NotBlank String password) {
    }
}
