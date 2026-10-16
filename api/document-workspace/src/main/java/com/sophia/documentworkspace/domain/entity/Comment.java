package com.sophia.documentworkspace.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends SoftDeletableEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "block_id")
    private UUID blockId;

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<UUID> mentions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<String>> reactions;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    public static Comment create(UUID workspaceId, UUID artifactId, UUID blockId,
                                 UUID parentCommentId, UUID authorId, String body,
                                 List<UUID> mentions) {
        Comment comment = new Comment();
        comment.workspaceId = workspaceId;
        comment.artifactId = artifactId;
        comment.blockId = blockId;
        comment.parentCommentId = parentCommentId;
        comment.authorId = authorId;
        comment.body = body;
        comment.mentions = mentions == null ? List.of() : List.copyOf(mentions);
        comment.reactions = new HashMap<>();
        comment.edited = false;
        return comment;
    }

    public void edit(String newBody, List<UUID> newMentions) {
        this.body = newBody;
        if (newMentions != null) {
            this.mentions = List.copyOf(newMentions);
        }
        this.edited = true;
    }

    public void erase(UUID deletedBy) {
        softDelete(deletedBy);
        this.body = null;
        this.mentions = List.of();
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    public void resolve(UUID userId) {
        this.resolvedAt = Instant.now();
        this.resolvedBy = userId;
    }

    public void unresolve() {
        this.resolvedAt = null;
        this.resolvedBy = null;
    }

    public void toggleReaction(String emoji, UUID userId) {
        Map<String, List<String>> updated = new HashMap<>(reactions == null ? Map.of() : reactions);
        List<String> users = new ArrayList<>(updated.getOrDefault(emoji, List.of()));
        String id = userId.toString();
        if (!users.remove(id)) {
            users.add(id);
        }
        if (users.isEmpty()) {
            updated.remove(emoji);
        } else {
            updated.put(emoji, users);
        }
        this.reactions = updated;
    }
}
