package com.sophia.notification.infrastructure.observability;

import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
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
public class EmailOutboxMetrics {

    private final EmailOutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    @PostConstruct
    void registerGauges() {
        Gauge.builder("notification.email.outbox.pending", pending, AtomicLong::get)
                .description("Emails queued and not yet delivered or given up on")
                .register(meterRegistry);
        Gauge.builder("notification.email.outbox.oldest.pending.age", oldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest undelivered email")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${sophia.email.backlog-refresh-interval:30s}")
    public void refresh() {
        try {
            pending.set(outboxRepository.countPending());
            Instant oldest = outboxRepository.oldestPendingCreatedAt();
            oldestPendingAgeSeconds.set(oldest == null
                    ? 0L
                    : Math.max(0L, Duration.between(oldest, Instant.now()).toSeconds()));
        } catch (RuntimeException e) {
            log.warn("Could not refresh the email outbox backlog gauges", e);
        }
    }
}
