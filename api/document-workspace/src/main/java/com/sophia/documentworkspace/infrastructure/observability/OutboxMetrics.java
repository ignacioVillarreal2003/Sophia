package com.sophia.documentworkspace.infrastructure.observability;

import com.sophia.documentworkspace.infrastructure.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;
    private final MeterRegistry meterRegistry;

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    @PostConstruct
    void registerGauges() {
        Gauge.builder("document.workspace.outbox.pending", pending, AtomicLong::get)
                .description("Domain events queued and not yet published to Kafka")
                .register(meterRegistry);
        Gauge.builder("document.workspace.outbox.oldest.pending.age", oldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished outbox event")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${sophia.events.relay-interval}")
    public void refresh() {
        try {
            pending.set(outboxEventRepository.countPending());
            Instant oldest = outboxEventRepository.oldestPendingCreatedAt();
            oldestPendingAgeSeconds.set(oldest == null
                    ? 0L
                    : Math.max(0L, Duration.between(oldest, Instant.now()).toSeconds()));
        } catch (RuntimeException e) {
            log.warn("Could not refresh the outbox backlog gauges", e);
        }
    }
}
