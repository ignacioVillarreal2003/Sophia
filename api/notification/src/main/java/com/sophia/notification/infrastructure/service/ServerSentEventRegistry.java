package com.sophia.notification.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class ServerSentEventRegistry implements MessageListener {

    private static final long EMITTER_TIMEOUT_MILLISECONDS = 30 * 60 * 1000L;
    private static final String CHANNEL_PREFIX = "sophia:notification:stream:";
    private static final int MAX_STREAMS_PER_USER = 5;

    private final Map<UUID, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final RedisMessageListenerContainer listenerContainer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ServerSentEventRegistry(RedisMessageListenerContainer listenerContainer,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper) {
        this.listenerContainer = listenerContainer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    record StreamEvent(UUID recipientId, String event, JsonNode payload) {
    }

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLISECONDS);
        List<SseEmitter> evicted = new ArrayList<>();
        emittersByUser.compute(userId, (id, existing) -> {
            List<SseEmitter> emitters = existing == null ? new CopyOnWriteArrayList<>() : existing;
            if (emitters.isEmpty()) {
                listenerContainer.addMessageListener(this, topicFor(id));
            }
            emitters.add(emitter);
            while (emitters.size() > MAX_STREAMS_PER_USER) {
                evicted.add(emitters.remove(0));
            }
            return emitters;
        });
        for (SseEmitter stale : evicted) {
            log.debug("Closing the oldest stream of {}: more than {} were open", userId, MAX_STREAMS_PER_USER);
            complete(stale);
        }
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));

        // Flush the response head straight away: without a first write the
        // client waits for the next heartbeat before it knows it is connected.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${sophia.stream.heartbeat-interval:20s}")
    public void sendHeartbeats() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    public void publish(UUID recipientId, String eventName, Object payload) {
        StreamEvent event;
        try {
            event = new StreamEvent(recipientId, eventName, objectMapper.valueToTree(payload));
        } catch (RuntimeException e) {
            log.warn("Could not serialize the '{}' event for {}", eventName, recipientId, e);
            return;
        }
        try {
            redisTemplate.convertAndSend(channelFor(recipientId), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Could not publish the '{}' event for {} over Redis; falling back to local delivery",
                    eventName, recipientId, e);
            deliverLocally(event);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliverLocally(objectMapper.readValue(message.getBody(), StreamEvent.class));
        } catch (IOException e) {
            log.warn("Discarding an unreadable notification stream event", e);
        }
    }

    public int connectionCount() {
        return emittersByUser.values().stream().mapToInt(List::size).sum();
    }

    private void deliverLocally(StreamEvent event) {
        List<SseEmitter> emitters = emittersByUser.get(event.recipientId());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.event())
                        .data(event.payload(), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                remove(event.recipientId(), emitter);
            }
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            log.debug("Stream was already closed", e);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                listenerContainer.removeMessageListener(this, topicFor(id));
                return null;
            }
            return emitters;
        });
    }

    private ChannelTopic topicFor(UUID userId) {
        return ChannelTopic.of(channelFor(userId));
    }

    private String channelFor(UUID userId) {
        return CHANNEL_PREFIX + userId;
    }
}
