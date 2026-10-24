package com.sophia.ai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "ai_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    /** A document an answer drew on, as the number the answer cites it with. */
    public record Citation(int index, UUID artifactId, String title) {
    }

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 12, updatable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<Citation> citations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ChatMessage of(UUID conversationId, String role, String content, List<Citation> citations) {
        ChatMessage m = new ChatMessage();
        m.id = UUID.randomUUID();
        m.conversationId = conversationId;
        m.role = role;
        m.content = content;
        m.citations = new ArrayList<>(citations);
        m.createdAt = Instant.now();
        return m;
    }

    public boolean isUser() {
        return USER.equals(role);
    }
}
