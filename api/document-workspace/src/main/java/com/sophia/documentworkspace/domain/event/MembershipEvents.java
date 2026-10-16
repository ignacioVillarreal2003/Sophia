package com.sophia.documentworkspace.domain.event;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of workspace memberships. Keyed by workspace id (not member id)
 * so membership changes are ordered together with the rest of the workspace stream.
 */
public final class MembershipEvents {

    static final String AGGREGATE_MEMBER = "workspace-member";

    private MembershipEvents() {
    }

    public record MemberAdded(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                              UUID memberId, UUID userId, WorkspaceRole role) implements DomainEvent {

        public static MemberAdded of(UUID workspaceId, UUID actorId, UUID memberId, UUID userId, WorkspaceRole role) {
            return new MemberAdded(UUID.randomUUID(), workspaceId, actorId, Instant.now(), memberId, userId, role);
        }

        @Override
        public String eventType() {
            return "workspace-member.added";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_MEMBER;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record MemberRemoved(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                UUID memberId, UUID userId, boolean left) implements DomainEvent {

        public static MemberRemoved of(UUID workspaceId, UUID actorId, UUID memberId, UUID userId, boolean left) {
            return new MemberRemoved(UUID.randomUUID(), workspaceId, actorId, Instant.now(), memberId, userId, left);
        }

        @Override
        public String eventType() {
            return "workspace-member.removed";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_MEMBER;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record MemberRoleChanged(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                    UUID memberId, UUID userId, WorkspaceRole previousRole,
                                    WorkspaceRole newRole) implements DomainEvent {

        public static MemberRoleChanged of(UUID workspaceId, UUID actorId, UUID memberId, UUID userId,
                                           WorkspaceRole previousRole, WorkspaceRole newRole) {
            return new MemberRoleChanged(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    memberId, userId, previousRole, newRole);
        }

        @Override
        public String eventType() {
            return "workspace-member.role-changed";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_MEMBER;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record MemberSuspended(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID memberId, UUID userId) implements DomainEvent {

        public static MemberSuspended of(UUID workspaceId, UUID actorId, UUID memberId, UUID userId) {
            return new MemberSuspended(UUID.randomUUID(), workspaceId, actorId, Instant.now(), memberId, userId);
        }

        @Override
        public String eventType() {
            return "workspace-member.suspended";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_MEMBER;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }

    public record MemberReactivated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                    UUID memberId, UUID userId) implements DomainEvent {

        public static MemberReactivated of(UUID workspaceId, UUID actorId, UUID memberId, UUID userId) {
            return new MemberReactivated(UUID.randomUUID(), workspaceId, actorId, Instant.now(), memberId, userId);
        }

        @Override
        public String eventType() {
            return "workspace-member.reactivated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_MEMBER;
        }

        @Override
        public UUID aggregateId() {
            return workspaceId;
        }
    }
}
