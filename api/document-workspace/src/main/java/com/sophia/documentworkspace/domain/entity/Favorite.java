package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A user's favorite (optionally pinned) artifact. Personal, never shared. */
@Entity
@Table(name = "favorites", uniqueConstraints =
        @UniqueConstraint(name = "uq_favorites_user_artifact", columnNames = {"user_id", "artifact_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(nullable = false)
    private boolean pinned;

    public static Favorite of(UUID workspaceId, UUID userId, UUID artifactId) {
        Favorite favorite = new Favorite();
        favorite.workspaceId = workspaceId;
        favorite.userId = userId;
        favorite.artifactId = artifactId;
        favorite.pinned = false;
        return favorite;
    }

    public void pin() {
        this.pinned = true;
    }

    public void unpin() {
        this.pinned = false;
    }
}
