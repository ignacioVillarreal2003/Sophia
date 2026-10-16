package com.sophia.documentworkspace.infrastructure.messaging;

import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.entity.OutboxEvent;
import com.sophia.documentworkspace.infrastructure.observability.DocumentWorkspaceMetrics;
import com.sophia.documentworkspace.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay(int relayMaxAttempts) {
        ApplicationProperties properties = new ApplicationProperties(null, null, null,
                new ApplicationProperties.Events("sophia.workspace.events", "sophia.notifications.commands",
                        Duration.ofSeconds(5), 10, relayMaxAttempts),
                null, null, null, null);
        return new OutboxRelay(outboxEventRepository, kafkaTemplate,
                properties, new DocumentWorkspaceMetrics(new SimpleMeterRegistry()));
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.forTopic("sophia.workspace.events", "WORKSPACE_CREATED", "aggregate-1",
                UUID.randomUUID(), "{}", "request-1");
    }

    @Test
    @DisplayName("publishes a pending event and marks it published")
    void publishesPendingEvent() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        relay(10).relayPendingEvents();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    @DisplayName("a transient failure keeps the row pending and counts the attempt")
    void transientFailureKeepsRowPending() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay(10).relayPendingEvents();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("exhausting the retry budget discards the row with a terminal marker")
    void exhaustedFailureDiscardsEvent() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay(1).relayPendingEvents();

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).contains("broker down");
    }
}
