package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What a person chose for their workspaces and the server acts on: how long a deleted workspace is kept. */
@Entity
@Table(name = "user_workspace_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWorkspacePreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "trash_retention_days", nullable = false)
    private int trashRetentionDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserWorkspacePreferences of(UUID userId, int trashRetentionDays) {
        UserWorkspacePreferences preferences = new UserWorkspacePreferences();
        preferences.userId = userId;
        preferences.trashRetentionDays = trashRetentionDays;
        preferences.updatedAt = Instant.now();
        return preferences;
    }

    public void keepTrashFor(int days) {
        this.trashRetentionDays = days;
        this.updatedAt = Instant.now();
    }
}
