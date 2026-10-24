package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.InvitationStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** An admin's offer to a platform user to join a workspace with a role; it takes effect when they accept. */
@Entity
@Table(name = "workspace_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvitation extends BaseEntity {

    /** How long an invitation stays open. */
    public static final Duration VALIDITY = Duration.ofDays(14);

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "invitee_user_id", nullable = false)
    private UUID inviteeUserId;

    @Column(name = "invitee_email", nullable = false, length = 320)
    private String inviteeEmail;

    @Column(name = "invitee_name", length = 255)
    private String inviteeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceRole role;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "invited_by_email", length = 320)
    private String invitedByEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public static WorkspaceInvitation create(UUID workspaceId, UUID inviteeUserId, String inviteeEmail,
                                             String inviteeName, WorkspaceRole role, UUID invitedBy,
                                             String invitedByEmail) {
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.workspaceId = workspaceId;
        invitation.inviteeUserId = inviteeUserId;
        invitation.inviteeEmail = inviteeEmail;
        invitation.inviteeName = inviteeName;
        invitation.role = role;
        invitation.invitedBy = invitedBy;
        invitation.invitedByEmail = invitedByEmail;
        invitation.status = InvitationStatus.PENDING;
        invitation.expiresAt = Instant.now().plus(VALIDITY);
        return invitation;
    }

    public boolean isOpen() {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(Instant.now());
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }

    public void answer(InvitationStatus outcome) {
        this.status = outcome;
        this.respondedAt = Instant.now();
    }
}
