package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Limits a platform administrator (or the system) sets on a workspace. One optional row per workspace. */
@Entity
@Table(name = "workspace_limits")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceLimits {

    @Id
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    /** Storage quota in megabytes; null means no limit. */
    @Column(name = "storage_quota_mb")
    private Long storageQuotaMb;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Highest storage alert already sent to the owner: 0 none, 80 almost full, 100 full. */
    @Column(name = "quota_alert_level", nullable = false)
    private int quotaAlertLevel;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    public static WorkspaceLimits forWorkspace(UUID workspaceId) {
        WorkspaceLimits limits = new WorkspaceLimits();
        limits.workspaceId = workspaceId;
        return limits;
    }

    public void recordQuotaAlert(int level) {
        this.quotaAlertLevel = level;
    }

    /** A value of null or 0 removes the limit. */
    public void changeStorageQuota(Long quotaMb, UUID updatedBy) {
        this.storageQuotaMb = quotaMb == null || quotaMb == 0 ? null : quotaMb;
        this.updatedBy = updatedBy;
    }
}
