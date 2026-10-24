package com.sophia.ai.domain.entity;

import com.sophia.ai.domain.entity.enums.AiFeature;
import com.sophia.ai.domain.entity.enums.GenerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What one call to the model cost. Prompts and answers are deliberately not kept. */
@Entity
@Table(name = "ai_generations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGeneration {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenerationStatus status;

    @Column(length = 64)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cache_read_tokens", nullable = false)
    private long cacheReadTokens;

    @Column(name = "cache_write_tokens", nullable = false)
    private long cacheWriteTokens;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AiGeneration of(UUID userId, String userEmail, UUID workspaceId, AiFeature feature,
                                  GenerationStatus status, String model, long input, long output, long cacheRead,
                                  long cacheWrite, long durationMs, String errorCode) {
        AiGeneration g = new AiGeneration();
        g.id = UUID.randomUUID();
        g.userId = userId;
        g.userEmail = userEmail;
        g.workspaceId = workspaceId;
        g.feature = feature;
        g.status = status;
        g.model = model;
        g.inputTokens = input;
        g.outputTokens = output;
        g.cacheReadTokens = cacheRead;
        g.cacheWriteTokens = cacheWrite;
        g.durationMs = durationMs;
        g.errorCode = errorCode;
        g.createdAt = Instant.now();
        return g;
    }
}
