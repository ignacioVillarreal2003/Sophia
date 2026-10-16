package com.sophia.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record UserLifecycleEvent(
        UserEventType eventType,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String locale,
        Instant occurredAt) {
}
