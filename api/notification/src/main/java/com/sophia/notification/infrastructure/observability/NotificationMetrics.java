package com.sophia.notification.infrastructure.observability;

import com.sophia.notification.infrastructure.service.ServerSentEventRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final ServerSentEventRegistry serverSentEventRegistry;

    @PostConstruct
    void registerGauges() {
        Gauge.builder("notification.sse.connections", serverSentEventRegistry,
                        ServerSentEventRegistry::connectionCount)
                .description("Open server-sent event connections")
                .register(meterRegistry);
    }

    public void commandProcessed(String category) {
        Counter.builder("notification.commands")
                .description("Notification commands by category and result")
                .tag("category", category == null ? "unknown" : category)
                .tag("result", "processed")
                .register(meterRegistry)
                .increment();
    }

    public void commandDuplicate(String category) {
        Counter.builder("notification.commands")
                .description("Notification commands by category and result")
                .tag("category", category == null ? "unknown" : category)
                .tag("result", "duplicate")
                .register(meterRegistry)
                .increment();
    }

    public void commandRejected(String category) {
        Counter.builder("notification.commands")
                .description("Notification commands by category and result")
                .tag("category", category == null ? "unknown" : category)
                .tag("result", "rejected")
                .register(meterRegistry)
                .increment();
    }

    public void inAppCreated(String category) {
        Counter.builder("notification.in.app.created")
                .description("In-application notifications created by category")
                .tag("category", category)
                .register(meterRegistry)
                .increment();
    }

    public void emailQueued(String category) {
        Counter.builder("notification.emails.queued")
                .description("Emails queued for delivery by category")
                .tag("category", category)
                .register(meterRegistry)
                .increment();
    }

    public void emailSkipped(String reason) {
        Counter.builder("notification.emails.skipped")
                .description("Emails skipped before queueing by reason")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void emailSent() {
        Counter.builder("notification.emails.dispatched")
                .description("Outbound emails by result")
                .tag("result", "sent")
                .register(meterRegistry)
                .increment();
    }

    public void emailFailed() {
        Counter.builder("notification.emails.dispatched")
                .description("Outbound emails by result")
                .tag("result", "failed")
                .register(meterRegistry)
                .increment();
    }

    public void rateLimited(String bucket) {
        Counter.builder("notification.rate.limited")
                .description("Calls rejected by a rate limit, by bucket")
                .tag("bucket", bucket)
                .register(meterRegistry)
                .increment();
    }

    public void deadLettered(String source) {
        Counter.builder("notification.messages.dead.lettered")
                .description("Records handed off to a dead letter topic by consumer")
                .tag("source", source)
                .register(meterRegistry)
                .increment();
    }

    public void userEventApplied(String eventType) {
        Counter.builder("notification.user.events")
                .description("User lifecycle events applied to the contact projection")
                .tag("type", eventType)
                .register(meterRegistry)
                .increment();
    }
}
