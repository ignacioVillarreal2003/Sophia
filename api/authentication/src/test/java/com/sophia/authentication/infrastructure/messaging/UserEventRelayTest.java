package com.sophia.authentication.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.UserEventOutbox;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventRelayTest {

    @Mock private UserEventOutboxRepository outboxRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UserEventRelay relay(boolean enabled) {
        ApplicationProperties base = TestFixtures.appProperties();
        ApplicationProperties properties = new ApplicationProperties(base.security(), base.oauth2(),
                base.notifications(), base.storage(), base.monitoring(), base.admin(),
                new ApplicationProperties.Events(enabled, "sophia.authentication.user-events",
                        Duration.ofSeconds(5), 100, 10, 3, 1));
        return new UserEventRelay(outboxRepository, kafkaTemplate, properties, objectMapper,
                TestFixtures.authenticationMetrics());
    }

    private UserEventOutbox pendingEntry() {
        return pendingEntry("USER_CREATED");
    }

    private UserEventOutbox pendingEntry(String eventType) {
        return UserEventOutbox.create(eventType, UUID.randomUUID(), "jane@test.local", "Jane", "Doe", "es-ES",
                "request-1", Instant.now());
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> capturedRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        return record.getValue();
    }

    @Test
    @DisplayName("does nothing while events are disabled")
    void skipsWhenDisabled() {
        relay(false).relayPendingEvents();

        verifyNoInteractions(outboxRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("publishes pending events and marks them as published")
    void publishesPendingEvents() {
        UserEventOutbox entry = pendingEntry();
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        relay(true).relayPendingEvents();

        ProducerRecord<String, String> record = capturedRecord();
        assertThat(record.topic()).isEqualTo("sophia.authentication.user-events");
        assertThat(record.key()).isEqualTo(entry.getUserId().toString());
        assertThat(entry.getPublishedAt()).isNotNull();
        assertThat(entry.getAttempts()).isZero();
    }

    @Test
    @DisplayName("the published event carries the contract the notification service consumes")
    void publishesTheAgreedContract() throws Exception {
        UserEventOutbox entry = pendingEntry();
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));

        relay(true).relayPendingEvents();

        ProducerRecord<String, String> record = capturedRecord();
        var event = objectMapper.readTree(record.value());
        assertThat(event.path("eventType").asText()).isEqualTo("USER_CREATED");
        assertThat(event.path("userId").asText()).isEqualTo(entry.getUserId().toString());
        assertThat(event.path("email").asText()).isEqualTo("jane@test.local");
        assertThat(event.path("firstName").asText()).isEqualTo("Jane");
        assertThat(event.path("lastName").asText()).isEqualTo("Doe");
        assertThat(event.path("locale").asText()).isEqualTo("es-ES");
        assertThat(event.path("occurredAt").isNull()).isFalse();

        assertThat(record.headers().lastHeader(MessageCorrelation.HEADER).value())
                .isEqualTo("request-1".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a broker failure keeps the row pending and counts the attempt")
    void brokerFailureKeepsRowPending() {
        UserEventOutbox entry = pendingEntry();
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay(true).relayPendingEvents();

        assertThat(entry.getPublishedAt()).isNull();
        assertThat(entry.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown event type is discarded instead of blocking the relay")
    void unknownEventTypeIsDiscarded() {
        UserEventOutbox entry = pendingEntry("USER_TELEPORTED");
        when(outboxRepository.findNextBatch(anyInt(), any(Pageable.class))).thenReturn(List.of(entry));

        relay(true).relayPendingEvents();

        verifyNoInteractions(kafkaTemplate);
        assertThat(entry.getPublishedAt()).isNotNull();
    }
}
