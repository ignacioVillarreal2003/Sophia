package com.sophia.authentication.domain.dto.response;

import com.sophia.authentication.domain.entity.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Audit trail entry")
public record AuditLogResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> details,
        String ipAddress,
        String userAgent,
        AuditLog.Result result,
        String requestId,
        Instant createdAt) {
}
