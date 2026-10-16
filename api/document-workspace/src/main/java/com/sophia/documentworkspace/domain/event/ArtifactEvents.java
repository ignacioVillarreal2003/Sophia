package com.sophia.documentworkspace.domain.event;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Domain events of the artifact hierarchy, keyed by artifact id. */
public final class ArtifactEvents {

    static final String AGGREGATE_ARTIFACT = "artifact";

    private ArtifactEvents() {
    }

    public record ArtifactCreated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID artifactId, ArtifactType type, String name,
                                  UUID parentId) implements DomainEvent {

        public static ArtifactCreated of(UUID workspaceId, UUID actorId, UUID artifactId,
                                         ArtifactType type, String name, UUID parentId) {
            return new ArtifactCreated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, type, name, parentId);
        }

        @Override
        public String eventType() {
            return "artifact.created";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record ArtifactUpdated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID artifactId, ArtifactType type,
                                  Snapshot before) implements DomainEvent {

        public record Snapshot(String name, String description) {
        }

        public static ArtifactUpdated of(UUID workspaceId, UUID actorId, UUID artifactId,
                                         ArtifactType type, Snapshot before) {
            return new ArtifactUpdated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, type, before);
        }

        @Override
        public String eventType() {
            return "artifact.updated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record ArtifactMoved(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                UUID artifactId, UUID previousParentId,
                                UUID newParentId) implements DomainEvent {

        public static ArtifactMoved of(UUID workspaceId, UUID actorId, UUID artifactId,
                                       UUID previousParentId, UUID newParentId) {
            return new ArtifactMoved(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, previousParentId, newParentId);
        }

        @Override
        public String eventType() {
            return "artifact.moved";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record ArtifactDuplicated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                     UUID sourceArtifactId, UUID newArtifactId) implements DomainEvent {

        public static ArtifactDuplicated of(UUID workspaceId, UUID actorId,
                                            UUID sourceArtifactId, UUID newArtifactId) {
            return new ArtifactDuplicated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    sourceArtifactId, newArtifactId);
        }

        @Override
        public String eventType() {
            return "artifact.duplicated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return newArtifactId;
        }
    }

    public record ArtifactTrashed(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID artifactId, ArtifactType type) implements DomainEvent {

        public static ArtifactTrashed of(UUID workspaceId, UUID actorId, UUID artifactId,
                                         ArtifactType type) {
            return new ArtifactTrashed(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId, type);
        }

        @Override
        public String eventType() {
            return "artifact.trashed";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record ArtifactRestored(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                   UUID artifactId) implements DomainEvent {

        public static ArtifactRestored of(UUID workspaceId, UUID actorId, UUID artifactId) {
            return new ArtifactRestored(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId);
        }

        @Override
        public String eventType() {
            return "artifact.restored";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }

    public record ArtifactPurged(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                 UUID artifactId) implements DomainEvent {

        public static ArtifactPurged of(UUID workspaceId, UUID actorId, UUID artifactId) {
            return new ArtifactPurged(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    artifactId);
        }

        @Override
        public String eventType() {
            return "artifact.purged";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_ARTIFACT;
        }

        @Override
        public UUID aggregateId() {
            return artifactId;
        }
    }
}
