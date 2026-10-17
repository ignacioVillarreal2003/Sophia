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

/** A non-member asking the workspace admins to be let in. */
@Entity
@Table(name = "access_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessRequest extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public static AccessRequest create(UUID workspaceId, UUID userId, String email, String message) {
        AccessRequest request = new AccessRequest();
        request.workspaceId = workspaceId;
        request.userId = userId;
        request.email = email;
        request.message = message == null || message.isBlank() ? null : message.strip();
        request.status = RequestStatus.PENDING;
        return request;
    }

    public void decide(RequestStatus outcome, UUID deciderId) {
        this.status = outcome;
        this.decidedBy = deciderId;
        this.decidedAt = Instant.now();
    }
}
