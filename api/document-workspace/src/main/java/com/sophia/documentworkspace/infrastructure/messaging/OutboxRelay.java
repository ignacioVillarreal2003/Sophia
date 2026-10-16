package com.sophia.documentworkspace.infrastructure.messaging;

import com.sophia.documentworkspace.infrastructure.repository.OutboxEventRepository;
import com.sophia.documentworkspace.domain.entity.OutboxEvent;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.infrastructure.observability.DocumentWorkspaceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relays pending outbox rows to Kafka. Keys messages by aggregate id so every
 * consumer sees a total order per aggregate. Failed sends are retried with an
 * attempt cap; exhausted rows stay in the table for operational inspection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int SEND_TIMEOUT_SECONDS = 10;
    private static final int PUBLISHED_RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplicationProperties properties;
    private final DocumentWorkspaceMetrics metrics;

    @Scheduled(fixedDelayString = "${sophia.events.relay-interval}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT1M")
    @Transactional
    public void relayPendingEvents() {
        ApplicationProperties.Events events = properties.events();
        List<OutboxEvent> batch = outboxEventRepository.findNextBatch(
                events.relayMaxAttempts(), PageRequest.of(0, events.relayBatchSize()));

        for (OutboxEvent event : batch) {
            String destination = event.getTopic() == null ? events.topic() : event.getTopic();
            try {
                kafkaTemplate.send(MessageCorrelation.stamp(
                                new ProducerRecord<>(destination, event.getAggregateId(), event.getPayload()),
                                event.getCorrelationId()))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
                metrics.outboxEventPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed(e.toString());
                metrics.outboxEventFailed();
                log.error("Outbox relay interrupted while publishing event {}", event.getId(), e);
                return;
            } catch (Exception e) {
                event.markFailed(e.toString());
                metrics.outboxEventFailed();
                if (event.getAttempts() >= events.relayMaxAttempts()) {
                    event.discard(e.toString());
                    log.error("Discarding outbox event {} after exhausting {} attempts",
                            event.getId(), event.getAttempts(), e);
                } else {
                    log.error("Failed to publish outbox event {} (attempt {})",
                            event.getId(), event.getAttempts(), e);
                }
            }
        }
    }

    /** Removes successfully published rows once they are old enough to be useless. */
    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    public void purgePublishedEvents() {
        Instant threshold = Instant.now().minus(PUBLISHED_RETENTION_DAYS, ChronoUnit.DAYS);
        long removed = outboxEventRepository.deleteByPublishedAtBefore(threshold);
        if (removed > 0) {
            log.info("Purged {} published outbox events older than {} days",
                    removed, PUBLISHED_RETENTION_DAYS);
        }
    }
}
