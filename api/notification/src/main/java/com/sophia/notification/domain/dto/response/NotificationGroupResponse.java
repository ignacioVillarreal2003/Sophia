package com.sophia.notification.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One collapsed group of notifications sharing a group key")
public record NotificationGroupResponse(
        String groupKey,
        long total,
        long unread,
        Instant firstAt,
        Instant lastAt) {
}
