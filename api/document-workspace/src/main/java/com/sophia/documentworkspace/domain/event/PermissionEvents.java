package com.sophia.documentworkspace.domain.event;

import com.sophia.documentworkspace.domain.entity.enums.AccessMode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Domain events of resource-level permissions (direct grants on folders and files),
 * keyed by workspace id.
 */
public final class PermissionEvents {

    static final String AGGREGATE_PERMISSION = "resource-permission";

    private PermissionEvents() {
    }

    public record AclGranted(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                             UUID aclEntryId, UUID resourceId,
                             UUID subjectUserId, Set<AccessMode> modes) implements DomainEvent {

        public static AclGranted of(UUID workspaceId, UUID actorId, UUID aclEntryId, UUID resourceId,
                                    UUID subjectUserId, Set<AccessMode> modes) {
            return new AclGranted(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    aclEntryId, resourceId, subjectUserId, modes);
        }

        @Override
        public String eventType() {
            return "resource-permission.acl-granted";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_PERMISSION;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record AclRevoked(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                             UUID aclEntryId, UUID resourceId) implements DomainEvent {

        public static AclRevoked of(UUID workspaceId, UUID actorId, UUID aclEntryId, UUID resourceId) {
            return new AclRevoked(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    aclEntryId, resourceId);
        }

        @Override
        public String eventType() {
            return "resource-permission.acl-revoked";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_PERMISSION;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

}
