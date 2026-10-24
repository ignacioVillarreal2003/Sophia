package com.sophia.ai.domain.entity;

import com.sophia.ai.domain.model.Enums.ChatMode;
import com.sophia.ai.domain.model.Enums.Stance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A running chat: who started it, what kind it is, and which documents it draws on. */
@Entity
@Table(name = "ai_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    /** A document attached to a conversation; the title is kept so the list renders without asking anyone. */
    public record SourceEntry(UUID artifactId, String title) {
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ChatMode mode;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Stance stance;

    @Column(length = 8)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<SourceEntry> sources = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Conversation start(UUID userId, UUID workspaceId, ChatMode mode, String title, String topic,
                                     Stance stance, String language, List<SourceEntry> sources) {
        Conversation c = new Conversation();
        c.id = UUID.randomUUID();
        c.userId = userId;
        c.workspaceId = workspaceId;
        c.mode = mode;
        c.title = title;
        c.topic = topic;
        c.stance = stance;
        c.language = language;
        c.sources = new ArrayList<>(sources);
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    public void rename(String title) {
        this.title = title;
    }

    public void useSources(List<SourceEntry> sources) {
        this.sources = new ArrayList<>(sources);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
