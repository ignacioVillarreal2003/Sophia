package com.sophia.documentworkspace.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Domain events of document content, keyed by document id. */
public final class DocumentEvents {

    static final String AGGREGATE_DOCUMENT = "document";

    private DocumentEvents() {
    }

    public record DocumentContentUpdated(UUID eventId, UUID workspaceId, UUID actorId,
                                         Instant occurredAt, UUID documentId, long contentVersion,
                                         int operationCount) implements DomainEvent {

        public static DocumentContentUpdated of(UUID workspaceId, UUID actorId, UUID documentId,
                                                long contentVersion, int operationCount) {
            return new DocumentContentUpdated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    documentId, contentVersion, operationCount);
        }

        @Override
        public String eventType() {
            return "document.content-updated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_DOCUMENT;
        }

        @Override
        public UUID aggregateId() {
            return documentId;
        }
    }

    public record VersionRestored(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID documentId, long restoredSeq,
                                  long newVersion) implements DomainEvent {

        public static VersionRestored of(UUID workspaceId, UUID actorId, UUID documentId,
                                         long restoredSeq, long newVersion) {
            return new VersionRestored(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    documentId, restoredSeq, newVersion);
        }

        @Override
        public String eventType() {
            return "document.version-restored";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_DOCUMENT;
        }

        @Override
        public UUID aggregateId() {
            return documentId;
        }
    }

    public record VersionNamed(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                               UUID documentId, long seq, String name) implements DomainEvent {

        public static VersionNamed of(UUID workspaceId, UUID actorId, UUID documentId,
                                      long seq, String name) {
            return new VersionNamed(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    documentId, seq, name);
        }

        @Override
        public String eventType() {
            return "document.version-named";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_DOCUMENT;
        }

        @Override
        public UUID aggregateId() {
            return documentId;
        }
    }
}
