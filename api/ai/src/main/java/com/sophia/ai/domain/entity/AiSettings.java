package com.sophia.ai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The single row of limits that administrators can change without a redeploy. */
@Entity
@Table(name = "ai_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "daily_tokens_per_user", nullable = false)
    private long dailyTokensPerUser;

    @Column(name = "daily_tokens_per_workspace", nullable = false)
    private long dailyTokensPerWorkspace;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AiSettings of(boolean enabled, long perUser, long perWorkspace) {
        AiSettings settings = new AiSettings();
        settings.id = SINGLETON_ID;
        settings.enabled = enabled;
        settings.dailyTokensPerUser = perUser;
        settings.dailyTokensPerWorkspace = perWorkspace;
        settings.updatedAt = Instant.now();
        return settings;
    }

    public void update(boolean enabled, long perUser, long perWorkspace, UUID by) {
        this.enabled = enabled;
        this.dailyTokensPerUser = perUser;
        this.dailyTokensPerWorkspace = perWorkspace;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
