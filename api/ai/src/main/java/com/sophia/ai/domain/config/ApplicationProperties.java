package com.sophia.ai.domain.config;

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
        @Valid @NotNull Ai ai,
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

    public record Ai(
            @NotBlank String provider,
            String apiKey,
            @NotBlank String baseUrl,
            @NotBlank String model,
            @NotNull Duration requestTimeout,
            @Min(0) int maxRetries,
            @Min(1000) int maxSourceChars,
            @Min(1) int maxSources,
            @Min(1) int maxConcurrentPerUser,
            @Valid @NotNull GenerationRateLimit generationRateLimit,
            @Valid @NotNull Defaults defaults,
            @Valid @NotNull Chat chat,
            @Valid @NotNull Upload upload,
            @NotBlank String workspaceApiUrl,
            @NotNull Duration usageRetention) {

        /** Never prints the key, wherever the configuration ends up being logged. */
        @Override
        public String toString() {
            return "Ai[provider=" + provider + ", model=" + model + ", apiKey=" + (hasApiKey() ? "***" : "<unset>") + "]";
        }

        public boolean isMock() {
            return "mock".equalsIgnoreCase(provider);
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public record GenerationRateLimit(@Min(1) int requests, @NotNull Duration window) {
        }

        /** Files the model may be shown (scans, photos of notes, PDFs); the provider caps images at 5 MB. */
        public record Upload(@Min(1024) long maxImageBytes, @Min(1024) long maxPdfBytes, @Min(1) int maxPdfPages) {
        }

        public record Defaults(boolean enabled, @Min(0) long dailyTokensPerUser, @Min(0) long dailyTokensPerWorkspace) {
        }

        /**
         * Conversations and the retrieval behind them.
         *
         * @param fullContextChars   up to this much document text goes into the prompt whole; beyond it only the
         *                           passages that match the question are sent
         * @param topChunks          passages sent when retrieving
         * @param chunkChars         target size of an indexed passage
         * @param maxIndexedChars    most document text one conversation may search through
         * @param historyMessages    most recent messages the model gets to see
         * @param maxMessages        most messages one conversation keeps
         * @param maxConversations   most conversations one person keeps
         * @param retention          conversations untouched for this long are deleted
         */
        public record Chat(@Min(1000) int fullContextChars, @Min(1) int topChunks, @Min(300) int chunkChars,
                           @Min(1000) int maxIndexedChars, @Min(2) int historyMessages, @Min(2) int maxMessages,
                           @Min(1) int maxConversations, @NotNull Duration retention) {
        }
    }

    public record Monitoring(@NotBlank String username, @NotBlank String password) {
    }
}
