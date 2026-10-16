package com.sophia.documentworkspace.domain.event;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;

import java.time.Instant;
import java.util.UUID;

/** Domain events of file storage, keyed by artifact id. */
public final class FileEvents {

    static final String AGGREGATE_FILE = "file";

    private FileEvents() {
    }

    public record FileUploaded(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                               UUID artifactId, ArtifactType type, String mimeType,
                               Long sizeBytes, String storageKey) implements DomainEvent {

        public static FileUploaded of(UUID workspaceId, UUID actorId, UUID artifactId,
                                      ArtifactType type, String mimeType, Long sizeBytes,
                                      String storageKey) {
            return new FileUploaded(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, type, mimeType, sizeBytes, storageKey);
        }

        @Override
        public String eventType() {
            return "file.uploaded";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_FILE;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record FileVersionAdded(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                   UUID artifactId, long versionSeq, String mimeType,
                                   Long sizeBytes, String storageKey) implements DomainEvent {

        public static FileVersionAdded of(UUID workspaceId, UUID actorId, UUID artifactId,
                                          long versionSeq, String mimeType, Long sizeBytes,
                                          String storageKey) {
            return new FileVersionAdded(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, versionSeq, mimeType, sizeBytes, storageKey);
        }

        @Override
        public String eventType() {
            return "file.version-added";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_FILE;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record FileVersionRestored(UUID eventId, UUID workspaceId, UUID actorId,
                                      Instant occurredAt, UUID artifactId,
                                      long restoredSeq) implements DomainEvent {

        public static FileVersionRestored of(UUID workspaceId, UUID actorId, UUID artifactId,
                                             long restoredSeq) {
            return new FileVersionRestored(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, restoredSeq);
        }

        @Override
        public String eventType() {
            return "file.version-restored";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_FILE;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record PreviewGenerated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                   UUID artifactId, String previewKind) implements DomainEvent {

        public static PreviewGenerated of(UUID workspaceId, UUID artifactId, String previewKind) {
            return new PreviewGenerated(UUID.randomUUID(), workspaceId, null, Instant.now(),
                    artifactId, previewKind);
        }

        @Override
        public String eventType() {
            return "file.preview-generated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_FILE;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }
}
