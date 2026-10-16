package com.sophia.authentication.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.NotificationCommandOutboxMessage;
import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
import com.sophia.authentication.support.TestFixtures;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCommandRelayTest {

    @Mock private NotificationCommandOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationCommandRelay relay(boolean enabled) {
        ApplicationProperties base = TestFixtures.appProperties();
        ApplicationProperties properties = new ApplicationProperties(base.security(), base.oauth2(),
                new ApplicationProperties.Notifications(enabled, "sophia.notifications.commands",
                        Duration.ofSeconds(5), 100, 10),
                base.storage(), base.monitoring(), base.admin(), base.events());
        return new NotificationCommandRelay(outboxRepository, kafkaTemplate, properties, objectMapper,
                TestFixtures.authenticationMetrics());
    }

    private NotificationCommandOutboxMessage pendingEntry(String payload) {
        return NotificationCommandOutboxMessage.create(
                "11111111-1111-1111-1111-111111111111", "request-1", payload, Instant.now());
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> capturedRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        return record.getValue();
    }

    @Test
    @DisplayName("does nothing while notifications are disabled")
    void skipsWhenDisabled() {
        relay(false).relayPendingCommands();

        verifyNoInteractions(outboxRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("publishes pending commands keyed by recipient and marks them as published")
    void publishesPendingCommands() {
        NotificationCommandOutboxMessage entry = pendingEntry("{\"category\":\"AUTHENTICATION_SECURITY\"}");
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        relay(true).relayPendingCommands();

        ProducerRecord<String, String> record = capturedRecord();
        assertThat(record.topic()).isEqualTo("sophia.notifications.commands");
        assertThat(record.key()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(record.value()).isEqualTo(entry.getPayload());
        assertThat(record.headers().lastHeader(MessageCorrelation.HEADER).value())
                .isEqualTo("request-1".getBytes(StandardCharsets.UTF_8));
        assertThat(entry.getPublishedAt()).isNotNull();
        assertThat(entry.getAttempts()).isZero();
    }

    @Test
    @DisplayName("a broker failure keeps the row pending and counts the attempt")
    void brokerFailureKeepsRowPending() {
        NotificationCommandOutboxMessage entry = pendingEntry("{\"category\":\"AUTHENTICATION_SECURITY\"}");
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay(true).relayPendingCommands();

        assertThat(entry.getPublishedAt()).isNull();
        assertThat(entry.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unparseable payload is discarded instead of blocking the relay")
    void unparseablePayloadIsDiscarded() {
        NotificationCommandOutboxMessage entry = pendingEntry("not-json{");
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));

        relay(true).relayPendingCommands();

        verifyNoInteractions(kafkaTemplate);
        assertThat(entry.getPublishedAt()).isNotNull();
    }
}
