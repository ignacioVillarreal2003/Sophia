package com.sophia.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.application.service.GenerationService.Prepared;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.llm.ModelGateway.Completion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns a generation into a server-sent event stream: {@code meta} first, then many {@code delta} events with the
 * text, then {@code done} with the token usage (or {@code error}). Comments are sent every few seconds so proxies do
 * not close a connection that is only waiting for the model to start answering. If the reader goes away the stream to
 * the model is closed, so nothing keeps generating (and billing) for nobody.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseStreamer {

    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long HEARTBEAT_SECONDS = 8;

    private final GenerationService service;
    private final ObjectMapper mapper;
    private final ExecutorService generationExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    public SseEmitter stream(AuthenticatedUser user, Prepared prepared) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        AtomicBoolean open = new AtomicBoolean(true);
        Object lock = new Object();
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(error -> open.set(false));

        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> send(emitter, open, lock, SseEmitter.event().comment("ping")),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        generationExecutor.submit(() -> {
            var post = prepared.post();
            boolean finished = false;
            try {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("feature", prepared.request().feature().name());
                if (post != null) {
                    meta.putAll(post.meta());
                }
                send(emitter, open, lock, event("meta", meta));
                Completion completion = service.stream(user, prepared,
                        text -> send(emitter, open, lock, event("delta", Map.of("t", text))));
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("truncated", completion.truncated());
                done.put("usage", Map.of("input", completion.usage().input() + completion.usage().cacheRead()
                        + completion.usage().cacheWrite(), "output", completion.usage().output()));
                if (post != null) {
                    finished = true;
                    done.putAll(post.complete(completion));
                }
                send(emitter, open, lock, event("done", done));
            } catch (ApiException e) {
                send(emitter, open, lock, event("error", Map.of("code", e.getCode().name(), "message", e.getMessage())));
            } catch (RuntimeException e) {
                log.error("Streaming {} failed", prepared.request().feature(), e);
                send(emitter, open, lock, event("error", Map.of("code", ErrorCode.INTERNAL_ERROR.name(),
                        "message", "The generation failed unexpectedly")));
            } finally {
                if (post != null && !finished) {
                    post.abort();
                }
                heartbeat.cancel(false);
                if (open.getAndSet(false)) {
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    private SseEmitter.SseEventBuilder event(String name, Map<String, Object> data) {
        try {
            return SseEmitter.event().name(name).data(mapper.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** False once the reader is gone (or the emitter finished): the caller should stop producing. */
    private boolean send(SseEmitter emitter, AtomicBoolean open, Object lock, SseEmitter.SseEventBuilder event) {
        if (!open.get()) {
            return false;
        }
        synchronized (lock) {
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException e) {
                open.set(false);
                return false;
            }
        }
    }
}
