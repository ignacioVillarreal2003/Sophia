package com.sophia.authentication.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.NotificationCommandOutboxMessage;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
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
public class NotificationCommandRelay {

    private static final int SEND_TIMEOUT_SECONDS = 10;

    private final NotificationCommandOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthenticationMetrics authenticationMetrics;

    @Scheduled(fixedDelayString = "${sophia.notifications.relay-interval}")
    @SchedulerLock(name = "notification-command-relay", lockAtMostFor = "PT4M", lockAtLeastFor = "PT2S")
    @Transactional
    public void relayPendingCommands() {
        ApplicationProperties.Notifications notifications = properties.notifications();
        if (!notifications.enabled()) {
            return;
        }
        List<NotificationCommandOutboxMessage> pending = outboxRepository.findNextBatch(
                notifications.relayMaxAttempts(), PageRequest.of(0, notifications.relayBatchSize()));
        for (NotificationCommandOutboxMessage entry : pending) {
            try {
                objectMapper.readTree(entry.getPayload());
                kafkaTemplate.send(MessageCorrelation.stamp(
                                new ProducerRecord<>(notifications.commandsTopic(), entry.getRecipientKey(),
                                        entry.getPayload()),
                                entry.getCorrelationId()))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                entry.markPublished();
                authenticationMetrics.notificationCommandPublished();
            } catch (JsonProcessingException e) {
                entry.discard(e.toString());
                authenticationMetrics.notificationCommandFailed();
                log.error("Discarding unparseable notification command {}", entry.getId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                entry.markFailed(e.toString());
                log.error("Notification command relay interrupted while publishing {}", entry.getId(), e);
                return;
            } catch (Exception e) {
                entry.markFailed(e.toString());
                log.warn("Failed to publish notification command {} (attempt {}): {}",
                        entry.getId(), entry.getAttempts(), e.getMessage());
                return;
            }
        }
    }
}
