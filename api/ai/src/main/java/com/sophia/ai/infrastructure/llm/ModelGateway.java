package com.sophia.ai.infrastructure.llm;

import com.sophia.ai.domain.entity.enums.AiFeature;

import java.util.List;
import java.util.Map;

/**
 * The one door to the language model. Everything above it works with plain prompts and plain results, so the
 * provider can be swapped (or faked in tests) without touching the features.
 */
public interface ModelGateway {

    /** False when the service has no credentials; features then answer AI_NOT_CONFIGURED. */
    boolean configured();

    String model();

    /** One request, one full answer. */
    Completion complete(ModelRequest request);

    /** Same request, but text is handed to {@code sink} as it is produced. The sink returns false to stop early. */
    Completion stream(ModelRequest request, TextSink sink);

    /**
     * @param feature  what this call is for (the mock answers by it; the log counts by it)
     * @param system   instructions that never change with the user's content
     * @param user     the task plus any source material
     * @param maxTokens ceiling for the answer
     * @param effort   how hard the model thinks: LOW for mechanical work, MEDIUM/HIGH for reasoning-heavy work
     * @param jsonSchema when set, the answer is constrained to this JSON Schema
     * @param history  earlier turns of a conversation, oldest first; {@code user} comes after them
     * @param cache    mark the prefix for prompt caching (worth it when the same long prefix is sent again soon)
     * @param attachments images or PDFs the model reads together with {@code user}
     */
    record ModelRequest(AiFeature feature, String system, String user, int maxTokens, Effort effort,
                        Map<String, Object> jsonSchema, List<Turn> history, boolean cache,
                        List<Attachment> attachments) {

        public ModelRequest {
            history = history == null ? List.of() : List.copyOf(history);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }

        public ModelRequest(AiFeature feature, String system, String user, int maxTokens, Effort effort,
                            Map<String, Object> jsonSchema, List<Turn> history, boolean cache) {
            this(feature, system, user, maxTokens, effort, jsonSchema, history, cache, List.of());
        }

        public ModelRequest(AiFeature feature, String system, String user, int maxTokens, Effort effort,
                            Map<String, Object> jsonSchema) {
            this(feature, system, user, maxTokens, effort, jsonSchema, List.of(), false, List.of());
        }

        public ModelRequest(AiFeature feature, String system, String user, int maxTokens, Effort effort) {
            this(feature, system, user, maxTokens, effort, null, List.of(), false, List.of());
        }
    }

    /** A file the model looks at: an image (PNG, JPEG, GIF, WebP) or a PDF, already checked and base64 encoded. */
    record Attachment(String mediaType, String base64) {

        public boolean isPdf() {
            return "application/pdf".equals(mediaType);
        }
    }

    /** An earlier message of a conversation; {@code user} is false for what the assistant said. */
    record Turn(boolean user, String text) {
    }

    enum Effort {LOW, MEDIUM, HIGH}

    /** Receives streamed text; returning false asks the gateway to stop (the reader went away). */
    @FunctionalInterface
    interface TextSink {
        boolean accept(String text);
    }

    record TokenUsage(long input, long output, long cacheRead, long cacheWrite) {

        public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0);

        public long total() {
            return input + output + cacheRead + cacheWrite;
        }
    }

    /**
     * @param text      everything the model wrote
     * @param refused   the model declined to answer
     * @param truncated the answer hit the token ceiling
     * @param stopped   the sink asked to stop before the end
     */
    record Completion(String text, TokenUsage usage, String model, boolean refused, boolean truncated, boolean stopped) {
    }
}
