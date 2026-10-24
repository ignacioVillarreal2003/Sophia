package com.sophia.ai.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Google Gemini through its REST API ({@code models/{model}:generateContent} and, for text that is shown as it is
 * written, {@code :streamGenerateContent?alt=sse}). The key travels in the {@code x-goog-api-key} header, never in the
 * URL, so it cannot end up in an access log. Structured results use {@code responseSchema}, so the answer is JSON for
 * the schema instead of text we hope to parse.
 *
 * <p>The thinking Gemini 3 does is billed as output and counts against {@code maxOutputTokens}, so the ceiling sent is
 * the feature's own plus some headroom for it; what the caller sees as usage includes the thinking.
 */
@Slf4j
public class GeminiGateway implements ModelGateway {

    /** Room for the model's own thinking on top of the answer a feature asked for. */
    static final int THINKING_HEADROOM = 8192;
    static final int MAX_OUTPUT = 65000;
    private static final Set<String> BLOCKED = Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII",
            "IMAGE_SAFETY", "RECITATION", "LANGUAGE");
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;

    public GeminiGateway(ApplicationProperties.Ai config) {
        this.baseUrl = config.baseUrl().replaceAll("/+$", "");
        this.apiKey = config.hasApiKey() ? config.apiKey() : null;
        this.model = config.model();
        this.timeout = config.requestTimeout();
        this.maxRetries = config.maxRetries();
    }

    @Override
    public boolean configured() {
        return apiKey != null;
    }

    @Override
    public String model() {
        return model;
    }

    // ---- one answer -------------------------------------------------------------------------------------------

    @Override
    public Completion complete(ModelRequest request) {
        requireKey();
        HttpResponse<InputStream> response = send(request, ":generateContent");
        try (InputStream body = response.body()) {
            JsonNode json = mapper.readTree(body);
            Parsed parsed = new Parsed();
            parsed.accept(json);
            return parsed.completion(model, false);
        } catch (IOException e) {
            throw upstream("The AI provider answered with something unreadable", e);
        }
    }

    // ---- as it is written -------------------------------------------------------------------------------------

    @Override
    public Completion stream(ModelRequest request, TextSink sink) {
        requireKey();
        HttpResponse<InputStream> response = send(request, ":streamGenerateContent?alt=sse");
        Parsed parsed = new Parsed();
        boolean stopped = false;
        try (InputStream body = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).strip();
                if (data.isEmpty() || data.equals("[DONE]")) {
                    continue;
                }
                int from = parsed.text.length();
                parsed.accept(mapper.readTree(data));
                String piece = parsed.text.substring(from);
                if (!piece.isEmpty() && !sink.accept(piece)) {
                    stopped = true;
                    break; // closing the body below ends the request, so nothing keeps generating for nobody
                }
            }
        } catch (IOException e) {
            throw upstream("The connection to the AI provider dropped", e);
        }
        return parsed.completion(model, stopped);
    }

    // ---- request ----------------------------------------------------------------------------------------------

    ObjectNode body(ModelRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.set("systemInstruction", textContent(null, request.system()));

        ArrayNode contents = root.putArray("contents");
        for (Turn turn : request.history()) {
            contents.add(textContent(turn.user() ? "user" : "model", turn.text()));
        }
        ObjectNode last = contents.addObject();
        last.put("role", "user");
        ArrayNode parts = last.putArray("parts");
        for (Attachment file : request.attachments()) {
            ObjectNode inline = parts.addObject().putObject("inlineData");
            inline.put("mimeType", file.mediaType());
            inline.put("data", file.base64());
        }
        parts.addObject().put("text", request.user());

        ObjectNode config = root.putObject("generationConfig");
        config.put("maxOutputTokens", Math.min(MAX_OUTPUT, request.maxTokens() + THINKING_HEADROOM));
        if (request.jsonSchema() != null) {
            config.put("responseMimeType", "application/json");
            config.set("responseSchema", schema(mapper.valueToTree(request.jsonSchema())));
        }
        return root;
    }

    private ObjectNode textContent(String role, String text) {
        ObjectNode content = mapper.createObjectNode();
        if (role != null) {
            content.put("role", role);
        }
        content.putArray("parts").addObject().put("text", text);
        return content;
    }

    /**
     * JSON Schema to Gemini's {@code Schema}: the same shape with upper-case type names and no
     * {@code additionalProperties}, which it does not accept.
     */
    static JsonNode schema(JsonNode node) {
        if (node.isArray()) {
            ArrayNode out = node.deepCopy();
            out.removeAll();
            node.forEach(item -> out.add(schema(item)));
            return out;
        }
        if (!node.isObject()) {
            return node;
        }
        ObjectNode out = node.deepCopy();
        out.removeAll();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            switch (field.getKey()) {
                case "additionalProperties" -> {
                    // not supported
                }
                case "type" -> out.put("type", field.getValue().asText().toUpperCase(java.util.Locale.ROOT));
                case "properties" -> {
                    ObjectNode properties = out.putObject("properties");
                    field.getValue().fields().forEachRemaining(p -> properties.set(p.getKey(), schema(p.getValue())));
                }
                default -> out.set(field.getKey(), schema(field.getValue()));
            }
        }
        return out;
    }

    // ---- transport --------------------------------------------------------------------------------------------

    /** Sends the request, retrying the failures that are worth a second try, and answers only with a success. */
    private HttpResponse<InputStream> send(ModelRequest request, String action) {
        String json;
        try {
            json = mapper.writeValueAsString(body(request));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.GENERATION_FAILED, HttpStatus.BAD_GATEWAY, "The request could not be built");
        }
        HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl + "/models/" + model + action))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        ApiException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                pause(attempt);
            }
            try {
                HttpResponse<InputStream> response = this.http.send(http, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status == 200) {
                    return response;
                }
                String message = errorMessage(response);
                last = translate(status, message);
                if (!RETRYABLE.contains(status)) {
                    throw last;
                }
            } catch (IOException e) {
                last = upstream("The AI provider did not answer in time. Try again.", e, HttpStatus.GATEWAY_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw upstream("The request was interrupted", e);
            }
        }
        throw last;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(Math.min(4000L, 700L * (1L << attempt)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String errorMessage(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            return mapper.readTree(body).path("error").path("message").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Maps what Google answers to errors the API can answer with; the provider's own text stays in the log. */
    static ApiException translate(int status, String message) {
        // An invalid key comes back as a 400 whose message says so, not as a 401.
        boolean badKey = status == 401 || status == 403 || (status == 400 && message.toLowerCase().contains("api key"));
        if (badKey) {
            log.error("The AI provider rejected our credentials (status {})", status);
            return new ApiException(ErrorCode.AI_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI provider rejected the service credentials");
        }
        if (status == 429) {
            log.warn("The AI provider is rate limiting us");
            return new ApiException(ErrorCode.UPSTREAM_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI provider is busy right now. Try again in a minute.");
        }
        log.error("The AI provider answered with status {}: {}", status, message);
        return new ApiException(ErrorCode.UPSTREAM_ERROR, HttpStatus.BAD_GATEWAY,
                "The AI provider could not complete the request");
    }

    private static ApiException upstream(String message, Throwable cause) {
        return upstream(message, cause, HttpStatus.BAD_GATEWAY);
    }

    private static ApiException upstream(String message, Throwable cause, HttpStatus status) {
        log.warn("{}", message, cause);
        return new ApiException(ErrorCode.UPSTREAM_ERROR, status, message);
    }

    private void requireKey() {
        if (apiKey == null) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI provider is not configured");
        }
    }

    // ---- response ---------------------------------------------------------------------------------------------

    /** What has been read of an answer so far; a streamed answer feeds it one chunk at a time. */
    private static final class Parsed {
        final StringBuilder text = new StringBuilder();
        String finishReason;
        boolean blockedPrompt;
        long prompt;
        long cached;
        long total;

        void accept(JsonNode chunk) {
            JsonNode error = chunk.get("error");
            if (error != null) {
                throw translate(error.path("code").asInt(502), error.path("message").asText(""));
            }
            if (chunk.path("promptFeedback").has("blockReason")) {
                blockedPrompt = true;
            }
            JsonNode candidate = chunk.path("candidates").path(0);
            for (JsonNode part : candidate.path("content").path("parts")) {
                // Thinking summaries come back as parts flagged "thought"; they are not part of the answer.
                if (part.has("text") && !part.path("thought").asBoolean(false)) {
                    text.append(part.get("text").asText());
                }
            }
            if (candidate.hasNonNull("finishReason")) {
                finishReason = candidate.get("finishReason").asText();
            }
            JsonNode usage = chunk.get("usageMetadata");
            if (usage != null) {
                prompt = usage.path("promptTokenCount").asLong(prompt);
                cached = usage.path("cachedContentTokenCount").asLong(cached);
                total = usage.path("totalTokenCount").asLong(total);
            }
        }

        Completion completion(String model, boolean stopped) {
            boolean refused = blockedPrompt || (finishReason != null && BLOCKED.contains(finishReason));
            // Billed output is what is left of the total after the prompt, thinking included.
            long output = Math.max(0, total - prompt);
            TokenUsage usage = new TokenUsage(Math.max(0, prompt - cached), output, cached, 0);
            return new Completion(text.toString(), usage, model, refused, "MAX_TOKENS".equals(finishReason), stopped);
        }
    }
}
