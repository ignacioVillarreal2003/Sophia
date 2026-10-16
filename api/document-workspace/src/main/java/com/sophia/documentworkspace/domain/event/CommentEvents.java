package com.sophia.documentworkspace.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain events of comments, keyed by the commented artifact id. */
public final class CommentEvents {

    static final String AGGREGATE_COMMENT = "comment";

    private CommentEvents() {
    }

    public record CommentCreated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                 UUID commentId, UUID artifactId, UUID blockId,
                                 UUID parentCommentId, List<UUID> mentions) implements DomainEvent {

        public static CommentCreated of(UUID workspaceId, UUID actorId, UUID commentId,
                                        UUID artifactId, UUID blockId, UUID parentCommentId,
                                        List<UUID> mentions) {
            return new CommentCreated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    commentId, artifactId, blockId, parentCommentId, mentions);
        }

        @Override
        public String eventType() {
            return "comment.created";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_COMMENT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record CommentUpdated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                 UUID commentId, UUID artifactId, String before) implements DomainEvent {

        public static CommentUpdated of(UUID workspaceId, UUID actorId, UUID commentId,
                                        UUID artifactId, String before) {
            return new CommentUpdated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    commentId, artifactId, before);
        }

        @Override
        public String eventType() {
            return "comment.updated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_COMMENT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record CommentResolved(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID commentId, UUID artifactId,
                                  boolean resolved) implements DomainEvent {

        public static CommentResolved of(UUID workspaceId, UUID actorId, UUID commentId,
                                         UUID artifactId, boolean resolved) {
            return new CommentResolved(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    commentId, artifactId, resolved);
        }

        @Override
        public String eventType() {
            return "comment.resolved";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_COMMENT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record CommentDeleted(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                 UUID commentId, UUID artifactId) implements DomainEvent {

        public static CommentDeleted of(UUID workspaceId, UUID actorId, UUID commentId,
                                        UUID artifactId) {
            return new CommentDeleted(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    commentId, artifactId);
        }

        @Override
        public String eventType() {
            return "comment.deleted";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_COMMENT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }
}
