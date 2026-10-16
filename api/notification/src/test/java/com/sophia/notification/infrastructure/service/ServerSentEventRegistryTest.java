package com.sophia.notification.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ServerSentEventRegistryTest {

    @Mock
    private RedisMessageListenerContainer listenerContainer;
    @Mock
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    private ServerSentEventRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServerSentEventRegistry(listenerContainer, redisTemplate, objectMapper);
    }

    @Test
    void notificationsGoOutOverRedisSoOtherInstancesCanDeliverThem() {
        registry.publish(userId, "notification", Map.of("title", "You were mentioned"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(channel()), body.capture());

        JsonNode event = readTree(body.getValue());
        assertThat(event.path("recipientId").asText()).isEqualTo(userId.toString());
        assertThat(event.path("event").asText()).isEqualTo("notification");
        assertThat(event.path("payload").path("title").asText()).isEqualTo("You were mentioned");
    }

    @Test
    void theRecipientChannelIsSubscribedOncePerInstanceRegardlessOfOpenStreams() {
        registry.register(userId);
        registry.register(userId);

        verify(listenerContainer, times(1)).addMessageListener(eq(registry), eq(ChannelTopic.of(channel())));
        assertThat(registry.connectionCount()).isEqualTo(2);
    }

    @Test
    void aRecipientWithoutStreamsOnThisInstanceIsIgnored() {
        registry.onMessage(new DefaultMessage(
                channel().getBytes(StandardCharsets.UTF_8),
                """
                        {"recipientId":"%s","event":"notification","payload":{"title":"Elsewhere"}}
                        """.formatted(userId).getBytes(StandardCharsets.UTF_8)), null);

        assertThat(registry.connectionCount()).isZero();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void anUnreadableStreamEventIsDiscardedInsteadOfKillingTheSubscription() {
        registry.onMessage(new DefaultMessage(
                channel().getBytes(StandardCharsets.UTF_8),
                "not json".getBytes(StandardCharsets.UTF_8)), null);

        assertThat(registry.connectionCount()).isZero();
    }

    private String channel() {
        return "sophia:notification:stream:" + userId;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
