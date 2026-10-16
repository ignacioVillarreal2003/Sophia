package com.sophia.documentworkspace.domain.event;


import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of the Workspace aggregate. All events are keyed by the
 * workspace id so consumers observe a total order per workspace.
 */
public final class WorkspaceEvents {

    static final String AGGREGATE_WORKSPACE = "workspace";

    private WorkspaceEvents() {
    }

    public record WorkspaceCreated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                   String name) implements DomainEvent {

        public static WorkspaceCreated of(UUID workspaceId, UUID actorId, String name) {
            return new WorkspaceCreated(UUID.randomUUID(), workspaceId, actorId, Instant.now(), name);
        }

        @Override
        public String eventType() {
            return "workspace.created";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceUpdated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                   Snapshot before) implements DomainEvent {

        public record Snapshot(String name, String description, String logoUrl,
                               String color, String language) {
        }

        public static WorkspaceUpdated of(UUID workspaceId, UUID actorId, Snapshot before) {
            return new WorkspaceUpdated(UUID.randomUUID(), workspaceId, actorId, Instant.now(), before);
        }

        @Override
        public String eventType() {
            return "workspace.updated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceSettingsUpdated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                           Snapshot before) implements DomainEvent {

        public record Snapshot(Integer trashRetentionDays, boolean publicSharingEnabled,
                               String timezone, String dateFormat) {
        }

        public static WorkspaceSettingsUpdated of(UUID workspaceId, UUID actorId, Snapshot before) {
            return new WorkspaceSettingsUpdated(UUID.randomUUID(), workspaceId, actorId, Instant.now(), before);
        }

        @Override
        public String eventType() {
            return "workspace.settings-updated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceDeleted(UUID eventId, UUID workspaceId, UUID actorId,
                                   Instant occurredAt) implements DomainEvent {

        public static WorkspaceDeleted of(UUID workspaceId, UUID actorId) {
            return new WorkspaceDeleted(UUID.randomUUID(), workspaceId, actorId, Instant.now());
        }

        @Override
        public String eventType() {
            return "workspace.deleted";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceRestored(UUID eventId, UUID workspaceId, UUID actorId,
                                    Instant occurredAt) implements DomainEvent {

        public static WorkspaceRestored of(UUID workspaceId, UUID actorId) {
            return new WorkspaceRestored(UUID.randomUUID(), workspaceId, actorId, Instant.now());
        }

        @Override
        public String eventType() {
            return "workspace.restored";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspacePurged(UUID eventId, UUID workspaceId, UUID actorId,
                                  Instant occurredAt) implements DomainEvent {

        public static WorkspacePurged of(UUID workspaceId, UUID actorId) {
            return new WorkspacePurged(UUID.randomUUID(), workspaceId, actorId, Instant.now());
        }

        @Override
        public String eventType() {
            return "workspace.purged";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceStatusChanged(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                         String from, String to) implements DomainEvent {

        public static WorkspaceStatusChanged of(UUID workspaceId, UUID actorId, String from, String to) {
            return new WorkspaceStatusChanged(UUID.randomUUID(), workspaceId, actorId, Instant.now(), from, to);
        }

        @Override
        public String eventType() {
            return "workspace.status-changed";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceLimitsUpdated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                         Long storageQuotaMb) implements DomainEvent {

        public static WorkspaceLimitsUpdated of(UUID workspaceId, UUID actorId, Long storageQuotaMb) {
            return new WorkspaceLimitsUpdated(UUID.randomUUID(), workspaceId, actorId, Instant.now(), storageQuotaMb);
        }

        @Override
        public String eventType() {
            return "workspace.limits-updated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceSuspended(UUID eventId, UUID workspaceId, UUID actorId,
                                     Instant occurredAt) implements DomainEvent {

        public static WorkspaceSuspended of(UUID workspaceId, UUID actorId) {
            return new WorkspaceSuspended(UUID.randomUUID(), workspaceId, actorId, Instant.now());
        }

        @Override
        public String eventType() {
            return "workspace.suspended";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record WorkspaceReactivated(UUID eventId, UUID workspaceId, UUID actorId,
                                       Instant occurredAt) implements DomainEvent {

        public static WorkspaceReactivated of(UUID workspaceId, UUID actorId) {
            return new WorkspaceReactivated(UUID.randomUUID(), workspaceId, actorId, Instant.now());
        }

        @Override
        public String eventType() {
            return "workspace.reactivated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record OwnershipTransferred(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                       UUID previousOwnerId, UUID newOwnerId) implements DomainEvent {

        public static OwnershipTransferred of(UUID workspaceId, UUID actorId,
                                              UUID previousOwnerId, UUID newOwnerId) {
            return new OwnershipTransferred(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    previousOwnerId, newOwnerId);
        }

        @Override
        public String eventType() {
            return "workspace.ownership-transferred";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_WORKSPACE;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }
}
