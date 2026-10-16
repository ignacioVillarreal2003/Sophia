package com.sophia.authentication.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.UserEventOutbox;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventRelay {

    private static final int SEND_TIMEOUT_SECONDS = 10;

    private final UserEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthenticationMetrics authenticationMetrics;

    @Scheduled(fixedDelayString = "${sophia.events.relay-interval}")
    @SchedulerLock(name = "user-event-relay", lockAtMostFor = "PT4M", lockAtLeastFor = "PT2S")
    @Transactional
    public void relayPendingEvents() {
        ApplicationProperties.Events events = properties.events();
        if (!events.enabled()) {
            return;
        }
        List<UserEventOutbox> pending = outboxRepository.findNextBatch(
                events.relayMaxAttempts(), PageRequest.of(0, events.relayBatchSize()));
        for (UserEventOutbox entry : pending) {
            try {
                kafkaTemplate.send(MessageCorrelation.stamp(
                                new ProducerRecord<>(events.userTopic(), entry.getUserId().toString(),
                                        objectMapper.writeValueAsString(toEvent(entry))),
                                entry.getCorrelationId()))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                entry.markPublished();
                authenticationMetrics.userEventPublished(entry.getEventType());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                entry.discard(e.toString());
                authenticationMetrics.userEventFailed(entry.getEventType());
                log.error("Discarding unserializable user event {}", entry.getId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                entry.markFailed(e.toString());
                log.error("User event relay interrupted while publishing event {}", entry.getId(), e);
                return;
            } catch (Exception e) {
                entry.markFailed(e.toString());
                log.warn("Failed to publish user event {} (attempt {}): {}",
                        entry.getId(), entry.getAttempts(), e.getMessage());
                return;
            }
        }
    }

    private UserLifecycleEvent toEvent(UserEventOutbox entry) {
        return new UserLifecycleEvent(
                UserEventType.valueOf(entry.getEventType()),
                entry.getUserId(),
                entry.getEmail(),
                entry.getFirstName(),
                entry.getLastName(),
                entry.getLocale(),
                entry.getOccurredAt());
    }
}
