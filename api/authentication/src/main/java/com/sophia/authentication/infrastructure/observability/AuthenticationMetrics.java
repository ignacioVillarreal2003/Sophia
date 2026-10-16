package com.sophia.authentication.infrastructure.observability;

import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
import io.micrometer.core.instrument.Counter;
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
public class AuthenticationMetrics {

    public static final String METHOD_PASSWORD = "password";
    public static final String METHOD_GOOGLE = "google";

    private final MeterRegistry meterRegistry;
    private final UserEventOutboxRepository userEventOutboxRepository;
    private final NotificationCommandOutboxRepository notificationCommandOutboxRepository;

    private final AtomicLong userEventsPending = new AtomicLong();
    private final AtomicLong userEventsOldestPendingAgeSeconds = new AtomicLong();
    private final AtomicLong notificationCommandsPending = new AtomicLong();
    private final AtomicLong notificationCommandsOldestPendingAgeSeconds = new AtomicLong();

    @PostConstruct
    void registerOutboxGauges() {
        Gauge.builder("authentication.user.events.outbox.pending", userEventsPending, AtomicLong::get)
                .description("User lifecycle events queued and not yet published to Kafka")
                .register(meterRegistry);
        Gauge.builder("authentication.user.events.outbox.oldest.pending.age",
                        userEventsOldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished user lifecycle event")
                .baseUnit("seconds")
                .register(meterRegistry);
        Gauge.builder("authentication.notification.commands.outbox.pending",
                        notificationCommandsPending, AtomicLong::get)
                .description("Notification commands queued and not yet published to Kafka")
                .register(meterRegistry);
        Gauge.builder("authentication.notification.commands.outbox.oldest.pending.age",
                        notificationCommandsOldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished notification command")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${sophia.events.relay-interval}")
    public void refreshOutboxGauges() {
        try {
            userEventsPending.set(userEventOutboxRepository.countPending());
            userEventsOldestPendingAgeSeconds.set(pendingAgeSeconds(userEventOutboxRepository.oldestPendingCreatedAt()));
            notificationCommandsPending.set(notificationCommandOutboxRepository.countPending());
            notificationCommandsOldestPendingAgeSeconds.set(
                    pendingAgeSeconds(notificationCommandOutboxRepository.oldestPendingCreatedAt()));
        } catch (RuntimeException e) {
            log.warn("Could not refresh the outbox backlog gauges", e);
        }
    }

    private long pendingAgeSeconds(Instant oldest) {
        return oldest == null ? 0L : Math.max(0L, Duration.between(oldest, Instant.now()).toSeconds());
    }

    public void loginSucceeded(String method) {
        Counter.builder("authentication.logins")
                .description("Login attempts by method and result")
                .tag("method", method)
                .tag("result", "success")
                .tag("reason", "none")
                .register(meterRegistry)
                .increment();
    }

    public void loginFailed(String method, String reason) {
        Counter.builder("authentication.logins")
                .description("Login attempts by method and result")
                .tag("method", method)
                .tag("result", "failure")
                .tag("reason", reason == null ? "unknown" : reason.toLowerCase())
                .register(meterRegistry)
                .increment();
    }

    public void accountLocked() {
        Counter.builder("authentication.lockouts")
                .description("Accounts locked after repeated failed logins")
                .register(meterRegistry)
                .increment();
    }

    public void userRegistered(String method) {
        Counter.builder("authentication.registrations")
                .description("New user registrations by method")
                .tag("method", method)
                .register(meterRegistry)
                .increment();
    }

    public void tokenIssued(String type) {
        Counter.builder("authentication.tokens.issued")
                .description("Tokens issued by type")
                .tag("type", type)
                .register(meterRegistry)
                .increment();
    }

    public void rateLimitRejected(String scope) {
        Counter.builder("authentication.rate.limit.rejections")
                .description("Requests rejected by the rate limiter")
                .tag("scope", scope)
                .register(meterRegistry)
                .increment();
    }

    public void notificationCommandPublished() {
        Counter.builder("authentication.notification.commands")
                .description("Notification commands relayed to Kafka by result")
                .tag("result", "published")
                .register(meterRegistry)
                .increment();
    }

    public void notificationCommandFailed() {
        Counter.builder("authentication.notification.commands")
                .description("Notification commands relayed to Kafka by result")
                .tag("result", "failed")
                .register(meterRegistry)
                .increment();
    }

    public void userEventPublished(String eventType) {
        userEvents(eventType, "published");
    }

    public void userEventFailed(String eventType) {
        userEvents(eventType, "failed");
    }

    private void userEvents(String eventType, String result) {
        Counter.builder("authentication.user.events")
                .description("User lifecycle events relayed to Kafka by type and result")
                .tag("type", eventType == null ? "unknown" : eventType)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
