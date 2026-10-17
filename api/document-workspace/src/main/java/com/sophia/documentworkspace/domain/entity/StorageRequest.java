package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;

import java.time.Instant;
import java.util.UUID;

/** The owner asking the platform administrators for a bigger storage quota. */
@Entity
@Table(name = "storage_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageRequest extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_mb", nullable = false)
    private long requestedMb;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestStatus status;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "granted_mb")
    private Long grantedMb;

    public static StorageRequest create(UUID workspaceId, UUID requestedBy, long requestedMb, String message) {
        StorageRequest request = new StorageRequest();
        request.workspaceId = workspaceId;
        request.requestedBy = requestedBy;
        request.requestedMb = requestedMb;
        request.message = message == null || message.isBlank() ? null : message.strip();
        request.status = RequestStatus.PENDING;
        return request;
    }

    public void approve(UUID adminId, long grantedMb) {
        this.status = RequestStatus.APPROVED;
        this.handledBy = adminId;
        this.handledAt = Instant.now();
        this.grantedMb = grantedMb;
    }

    public void deny(UUID adminId) {
        this.status = RequestStatus.DENIED;
        this.handledBy = adminId;
        this.handledAt = Instant.now();
    }
}
