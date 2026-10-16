package com.sophia.notification.infrastructure.observability;

import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxMetricsTest {

    @Mock
    private EmailOutboxRepository outboxRepository;

    private MeterRegistry registry;
    private EmailOutboxMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new EmailOutboxMetrics(outboxRepository, registry);
        metrics.registerGauges();
    }

    @Test
    void theBacklogDepthAndAgeAreExposed() {
        when(outboxRepository.countPending()).thenReturn(7L);
        when(outboxRepository.oldestPendingCreatedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(20)));

        metrics.refresh();

        assertThat(gauge("notification.email.outbox.pending")).isEqualTo(7.0);
        assertThat(gauge("notification.email.outbox.oldest.pending.age")).isBetween(1190.0, 1210.0);
    }

    @Test
    void anEmptyOutboxReportsNoAge() {
        when(outboxRepository.countPending()).thenReturn(0L);
        when(outboxRepository.oldestPendingCreatedAt()).thenReturn(null);

        metrics.refresh();

        assertThat(gauge("notification.email.outbox.pending")).isZero();
        assertThat(gauge("notification.email.outbox.oldest.pending.age")).isZero();
    }

    @Test
    void aClockSkewedRowDoesNotProduceANegativeAge() {
        when(outboxRepository.countPending()).thenReturn(1L);
        when(outboxRepository.oldestPendingCreatedAt()).thenReturn(Instant.now().plusSeconds(120));

        metrics.refresh();

        assertThat(gauge("notification.email.outbox.oldest.pending.age")).isZero();
    }

    @Test
    void aDatabaseHiccupLeavesTheLastKnownValuesInPlace() {
        when(outboxRepository.countPending()).thenReturn(3L);
        when(outboxRepository.oldestPendingCreatedAt()).thenReturn(Instant.now());
        metrics.refresh();

        when(outboxRepository.countPending()).thenThrow(new QueryTimeoutException("database is busy"));

        assertThatCode(() -> metrics.refresh()).doesNotThrowAnyException();
        assertThat(gauge("notification.email.outbox.pending")).isEqualTo(3.0);
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
