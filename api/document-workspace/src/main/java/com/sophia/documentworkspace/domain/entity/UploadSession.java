package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Server-side record of one multipart upload: ties the S3 upload id to the
 * workspace, the initiating user and the declared file facts, so completion
 * can validate ownership, size and checksum before the object becomes an
 * artifact. Stale sessions are aborted by a scheduled sweep.
 */
@Entity
@Table(name = "upload_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadSession extends BaseEntity {

    public enum Status {
        ACTIVE,
        COMPLETED,
        ABORTED
    }

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "s3_upload_id", nullable = false, length = 500)
    private String s3UploadId;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "declared_mime_type", length = 255)
    private String declaredMimeType;

    @Column(name = "declared_size_bytes")
    private Long declaredSizeBytes;

    @Column(name = "declared_checksum_sha256", length = 64)
    private String declaredChecksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static UploadSession start(UUID workspaceId, UUID parentId, UUID initiatedBy,
                                      String storageKey, String s3UploadId, String filename,
                                      String declaredMimeType, Long declaredSizeBytes,
                                      String declaredChecksumSha256, Instant expiresAt) {
        UploadSession session = new UploadSession();
        session.workspaceId = workspaceId;
        session.parentId = parentId;
        session.initiatedBy = initiatedBy;
        session.storageKey = storageKey;
        session.s3UploadId = s3UploadId;
        session.filename = filename;
        session.declaredMimeType = declaredMimeType;
        session.declaredSizeBytes = declaredSizeBytes;
        session.declaredChecksumSha256 = declaredChecksumSha256;
        session.status = Status.ACTIVE;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public void complete() {
        this.status = Status.COMPLETED;
    }

    public void abort() {
        this.status = Status.ABORTED;
    }
}
