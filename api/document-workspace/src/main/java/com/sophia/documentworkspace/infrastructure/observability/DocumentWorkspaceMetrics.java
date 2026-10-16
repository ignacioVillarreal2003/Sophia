package com.sophia.documentworkspace.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentWorkspaceMetrics {

    private final MeterRegistry meterRegistry;

    public void outboxEventPublished() {
        outboxEvents("published");
    }

    public void outboxEventFailed() {
        outboxEvents("failed");
    }

    public void rateLimited(String bucket) {
        Counter.builder("document.workspace.rate.limited")
                .description("Calls rejected by a rate limit, by bucket")
                .tag("bucket", bucket)
                .register(meterRegistry)
                .increment();
    }

    private void outboxEvents(String result) {
        Counter.builder("document.workspace.outbox.events")
                .description("Domain events relayed to Kafka by result")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
