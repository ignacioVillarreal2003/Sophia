package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Tenant root: every artifact, member, comment and permission lives inside
 * exactly one workspace, and workspaces are fully isolated from each other.
 */
@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends SoftDeletableEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Storage key of the logo image (the API exposes a URL derived from it). */
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(length = 16)
    private String color;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceStatus status;

    /** Days the trash keeps items before purging them; null means the system default. */
    @Column(name = "trash_retention_days")
    private Integer trashRetentionDays;

    @Column(name = "public_sharing_enabled", nullable = false)
    private boolean publicSharingEnabled;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "date_format", nullable = false, length = 32)
    private String dateFormat;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static Workspace create(String name,
                                   String description,
                                   String logoUrl,
                                   String color,
                                   String language,
                                   UUID ownerId) {
        Workspace workspace = new Workspace();
        workspace.name = name;
        workspace.description = description;
        workspace.logoUrl = logoUrl;
        workspace.color = color;
        workspace.language = language;
        workspace.ownerId = ownerId;
        workspace.status = WorkspaceStatus.ACTIVE;
        workspace.publicSharingEnabled = false;
        workspace.timezone = "UTC";
        workspace.dateFormat = "yyyy-MM-dd";
        workspace.updatedBy = ownerId;
        return workspace;
    }

    /** PATCH semantics: only non-null arguments overwrite the current value. */
    public void updateDetails(String name,
                              String description,
                              String color,
                              String language) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (color != null) {
            this.color = color;
        }
        if (language != null) {
            this.language = language;
        }
    }

    /** Stores the storage key of the logo object, or clears it with {@code null}. */
    public void changeLogo(String storageKey) {
        this.logoUrl = storageKey;
    }

    /** PATCH semantics; a retention of 0 clears the override (back to the system default). */
    public void updatePreferences(Integer trashRetentionDays, Boolean publicSharingEnabled,
                                  String timezone, String dateFormat) {
        if (trashRetentionDays != null) {
            this.trashRetentionDays = trashRetentionDays == 0 ? null : trashRetentionDays;
        }
        if (publicSharingEnabled != null) {
            this.publicSharingEnabled = publicSharingEnabled;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (dateFormat != null) {
            this.dateFormat = dateFormat;
        }
    }

    public void markUpdatedBy(UUID userId) {
        this.updatedBy = userId;
    }

    public void changeStatus(WorkspaceStatus status) {
        this.status = status;
    }

    public void transferOwnershipTo(UUID newOwnerId) {
        this.ownerId = newOwnerId;
    }

    public void suspend() {
        this.status = WorkspaceStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = WorkspaceStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == WorkspaceStatus.ACTIVE && !isDeleted();
    }
}
