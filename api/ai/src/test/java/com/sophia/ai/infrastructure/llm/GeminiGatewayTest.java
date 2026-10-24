package com.sophia.ai.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.ai.TestSupport;
import com.sophia.ai.domain.config.ApplicationProperties.Ai;
import com.sophia.ai.domain.entity.enums.AiFeature;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.llm.ModelGateway.Attachment;
import com.sophia.ai.infrastructure.llm.ModelGateway.Completion;
import com.sophia.ai.infrastructure.llm.ModelGateway.Effort;
import com.sophia.ai.infrastructure.llm.ModelGateway.ModelRequest;
import com.sophia.ai.infrastructure.llm.ModelGateway.Turn;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The gateway against a stand-in for Google's API running on this machine: what it sends and how it reads the answers. */
class GeminiGatewayTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** What the stand-in answers with: status and body, for the request that arrived. */
    private record Reply(int status, String body, String contentType) {
        static Reply json(int status, String body) {
            return new Reply(status, body, "application/json");
        }

        static Reply sse(String... events) {
            StringBuilder out = new StringBuilder();
            for (String event : events) {
                out.append("data: ").append(event).append("\r\n\r\n");
            }
            return new Reply(200, out.toString(), "text/event-stream");
        }
    }

    private record Seen(String path, String key, JsonNode body) {
    }

    private HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private volatile Function<Integer, Reply> answer = n -> Reply.json(200, "{}");
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            seen.add(new Seen(exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                    JSON.readTree(request)));
            Reply reply = answer.apply(calls.incrementAndGet());
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiGateway gateway(String key, int retries) {
        Ai base = TestSupport.ai("gemini", 2);
        Ai ai = new Ai(base.provider(), key, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta", "gemini-test",
                Duration.ofSeconds(5), retries, base.maxSourceChars(), base.maxSources(), base.maxConcurrentPerUser(),
                base.generationRateLimit(), base.defaults(), base.chat(), base.upload(), base.workspaceApiUrl(),
                base.usageRetention());
        return new GeminiGateway(ai);
    }

    private static ModelRequest request() {
        return new ModelRequest(AiFeature.NOTES, "be careful", "write notes", 1000, Effort.MEDIUM);
    }

    private static String answer(String text, String finish, int prompt, int total) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}]},\"finishReason\":\"" + finish
                + "\"}],\"usageMetadata\":{\"promptTokenCount\":" + prompt + ",\"totalTokenCount\":" + total + "}}";
    }

    private static String quote(String s) {
        try {
            return JSON.writeValueAsString(s);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- request ----------------------------------------------------------------------------------------------

    @Test
    void theKeyGoesInTheHeaderNotTheUrlAndTheBodyCarriesEverything() {
        answer = n -> Reply.json(200, answer("hola", "STOP", 10, 30));
        var withHistory = new ModelRequest(AiFeature.CHAT, "system text", "pregunta", 1500, Effort.LOW, null,
                List.of(new Turn(true, "antes"), new Turn(false, "respuesta")), true,
                List.of(new Attachment("image/png", "QUJD")));
        gateway("secret-key", 0).complete(withHistory);

        Seen call = seen.get(0);
        assertThat(call.path()).isEqualTo("/v1beta/models/gemini-test:generateContent");
        assertThat(call.key()).isEqualTo("secret-key");
        assertThat(call.path()).doesNotContain("secret-key");
        JsonNode body = call.body();
        assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("system text");
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/contents/1/role").asText()).isEqualTo("model");
        assertThat(body.at("/contents/2/parts/0/inlineData/mimeType").asText()).isEqualTo("image/png");
        assertThat(body.at("/contents/2/parts/0/inlineData/data").asText()).isEqualTo("QUJD");
        assertThat(body.at("/contents/2/parts/1/text").asText()).isEqualTo("pregunta");
        // The answer plus room for the model's own thinking.
        assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(1500 + GeminiGateway.THINKING_HEADROOM);
    }

    @Test
    void structuredOutputUsesGeminisSchemaDialect() {
        answer = n -> Reply.json(200, answer("{\"a\":1}", "STOP", 1, 2));
        Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false, "required", List.of("a"),
                "properties", Map.of("a", Map.of("type", "integer"), "list", Map.of("type", "array",
                        "items", Map.of("type", "string", "enum", List.of("X", "Y")))));
        gateway("k", 0).complete(new ModelRequest(AiFeature.TAGS, "s", "u", 100, Effort.LOW, schema));

        JsonNode config = seen.get(0).body().path("generationConfig");
        assertThat(config.path("responseMimeType").asText()).isEqualTo("application/json");
        JsonNode sent = config.path("responseSchema");
        assertThat(sent.path("type").asText()).isEqualTo("OBJECT");
        assertThat(sent.has("additionalProperties")).isFalse();
        assertThat(sent.at("/properties/a/type").asText()).isEqualTo("INTEGER");
        assertThat(sent.at("/properties/list/items/type").asText()).isEqualTo("STRING");
        assertThat(sent.at("/properties/list/items/enum/1").asText()).isEqualTo("Y");
        assertThat(sent.at("/required/0").asText()).isEqualTo("a");
    }

    @Test
    void everySchemaTheFeaturesSendConvertsWithoutLeavingJsonSchemaOnlyKeywords() throws Exception {
        for (Map<String, Object> schema : List.of(com.sophia.ai.application.prompt.OutputSchemas.deck(),
                com.sophia.ai.application.prompt.OutputSchemas.mindMap(), com.sophia.ai.application.prompt.OutputSchemas.quiz(),
                com.sophia.ai.application.prompt.OutputSchemas.grade(), com.sophia.ai.application.prompt.OutputSchemas.studyPlan(),
                com.sophia.ai.application.prompt.OutputSchemas.perspectives(), com.sophia.ai.application.prompt.OutputSchemas.debate(),
                com.sophia.ai.application.prompt.OutputSchemas.gaps(), com.sophia.ai.application.prompt.OutputSchemas.table(),
                com.sophia.ai.application.prompt.OutputSchemas.diagram(), com.sophia.ai.application.prompt.OutputSchemas.tags())) {
            String converted = JSON.writeValueAsString(GeminiGateway.schema(JSON.valueToTree(schema)));
            assertThat(converted).doesNotContain("additionalProperties").doesNotContain("\"type\":\"object\"")
                    .doesNotContain("\"type\":\"string\"").doesNotContain("\"type\":\"array\"");
        }
    }

    // ---- one answer -------------------------------------------------------------------------------------------

    @Test
    void readsTheTextAndCountsThinkingAsOutput() {
        answer = n -> Reply.json(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"pensando\",\"thought\":true},"
                + "{\"text\":\"Hola \"},{\"text\":\"mundo\"}]},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":100,\"cachedContentTokenCount\":40,\"totalTokenCount\":400}}");
        Completion c = gateway("k", 0).complete(request());

        assertThat(c.text()).isEqualTo("Hola mundo");
        assertThat(c.usage().input()).isEqualTo(60);
        assertThat(c.usage().cacheRead()).isEqualTo(40);
        assertThat(c.usage().output()).isEqualTo(300);
        assertThat(c.refused()).isFalse();
        assertThat(c.truncated()).isFalse();
        assertThat(c.model()).isEqualTo("gemini-test");
    }

    @Test
    void flagsATruncatedAnswerAndABlockedOne() {
        answer = n -> Reply.json(200, answer("cortado", "MAX_TOKENS", 1, 9));
        assertThat(gateway("k", 0).complete(request()).truncated()).isTrue();

        answer = n -> Reply.json(200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");
        assertThat(gateway("k", 0).complete(request()).refused()).isTrue();

        answer = n -> Reply.json(200, "{\"promptFeedback\":{\"blockReason\":\"PROHIBITED_CONTENT\"}}");
        assertThat(gateway("k", 0).complete(request()).refused()).isTrue();
    }

    // ---- streaming --------------------------------------------------------------------------------------------

    @Test
    void streamsPiecesAsTheyArriveAndReadsTheUsageFromTheLastOne() {
        answer = n -> Reply.sse(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Uno \"}]}}]}",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"dos \"},{\"text\":\"tres\"}]},\"finishReason\":\"STOP\"}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":10,\"totalTokenCount\":25}}");
        List<String> pieces = new ArrayList<>();
        Completion c = gateway("k", 0).stream(request(), piece -> pieces.add(piece));

        assertThat(seen.get(0).path()).isEqualTo("/v1beta/models/gemini-test:streamGenerateContent?alt=sse");
        assertThat(pieces).containsExactly("Uno ", "dos tres");
        assertThat(c.text()).isEqualTo("Uno dos tres");
        assertThat(c.stopped()).isFalse();
        assertThat(c.usage().output()).isEqualTo(15);
    }

    @Test
    void stopsReadingWhenTheSinkSaysSo() {
        answer = n -> Reply.sse(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"a\"}]}}]}",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"b\"}]}}]}",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"c\"}]}}]}");
        List<String> pieces = new ArrayList<>();
        Completion c = gateway("k", 0).stream(request(), piece -> {
            pieces.add(piece);
            return pieces.size() < 2;
        });
        assertThat(pieces).containsExactly("a", "b");
        assertThat(c.stopped()).isTrue();
    }

    @Test
    void anErrorInsideTheStreamIsTranslated() {
        answer = n -> Reply.sse("{\"error\":{\"code\":429,\"message\":\"quota\"}}");
        assertThatThrownBy(() -> gateway("k", 0).stream(request(), piece -> true)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR));
    }

    // ---- errors -----------------------------------------------------------------------------------------------

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void withoutAKeyNothingIsSent() {
        GeminiGateway none = gateway("", 0);
        assertThat(none.configured()).isFalse();
        assertCode(() -> none.complete(request()), ErrorCode.AI_NOT_CONFIGURED);
        assertThat(seen).isEmpty();
    }

    @Test
    void anInvalidKeyIsReportedAsNotConfiguredWhateverStatusItComesWith() {
        answer = n -> Reply.json(400, "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}");
        assertCode(() -> gateway("bad", 2).complete(request()), ErrorCode.AI_NOT_CONFIGURED);
        assertThat(calls.get()).isEqualTo(1); // not worth retrying

        answer = n -> Reply.json(403, "{\"error\":{\"message\":\"denied\"}}");
        assertCode(() -> gateway("bad", 2).complete(request()), ErrorCode.AI_NOT_CONFIGURED);
    }

    @Test
    void aBadRequestIsAnUpstreamErrorAndIsNotRetried() {
        answer = n -> Reply.json(400, "{\"error\":{\"message\":\"unsupported schema\"}}");
        assertCode(() -> gateway("k", 2).complete(request()), ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rateLimitsAndServerErrorsAreRetriedThenGiveUp() {
        answer = n -> n < 3 ? Reply.json(503, "{\"error\":{\"message\":\"overloaded\"}}") : Reply.json(200, answer("ok", "STOP", 1, 2));
        assertThat(gateway("k", 2).complete(request()).text()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);

        calls.set(0);
        answer = n -> Reply.json(429, "{\"error\":{\"message\":\"slow down\"}}");
        assertCode(() -> gateway("k", 1).complete(request()), ErrorCode.UPSTREAM_ERROR);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void anUnreachableProviderIsAnUpstreamError() {
        GeminiGateway gateway = gateway("k", 0);
        server.stop(0);
        assertCode(() -> gateway.complete(request()), ErrorCode.UPSTREAM_ERROR);
    }
}
