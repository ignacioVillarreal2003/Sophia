package com.sophia.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    public enum Result {
        SUCCESS,
        FAILURE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", updatable = false, length = 255)
    private String userEmail;

    @Column(nullable = false, updatable = false, length = 128)
    private String action;

    @Column(name = "resource_type", updatable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 255)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Result result;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @Column(name = "request_id", updatable = false, length = 64)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditLog of(UUID userId, String userEmail, String action, String resourceType,
                              String resourceId, Result result, String ipAddress, String userAgent,
                              String requestId, Map<String, Object> details) {
        AuditLog entry = new AuditLog();
        entry.userId = userId;
        entry.userEmail = userEmail;
        entry.action = action;
        entry.resourceType = resourceType;
        entry.resourceId = resourceId;
        entry.result = result;
        entry.ipAddress = ipAddress;
        entry.userAgent = userAgent;
        entry.requestId = requestId;
        entry.details = details;
        return entry;
    }
}
