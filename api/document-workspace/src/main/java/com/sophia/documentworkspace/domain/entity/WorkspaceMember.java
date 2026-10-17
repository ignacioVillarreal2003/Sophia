package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership of a platform user (owned by the authentication service, referenced
 * here only by id/email) in a workspace, carrying its role (ADMIN, EDITOR or VIEWER).
 * The workspace owner is {@code Workspace.ownerId} and is stored as an ADMIN member.
 */
@Entity
@Table(name = "workspace_members", uniqueConstraints =
        @UniqueConstraint(name = "uq_workspace_members_user", columnNames = {"workspace_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MemberStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** When this member pinned the workspace to the top of their list; null when it is not pinned. */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    public static WorkspaceMember ownerOf(Workspace workspace, UUID userId, String email, String displayName) {
        WorkspaceMember member = base(workspace, userId, email, displayName);
        member.role = WorkspaceRole.ADMIN;
        member.invitedBy = null;
        return member;
    }

    public static WorkspaceMember join(Workspace workspace,
                                       UUID userId,
                                       String email,
                                       String displayName,
                                       WorkspaceRole role,
                                       UUID invitedBy) {
        WorkspaceMember member = base(workspace, userId, email, displayName);
        member.role = role;
        member.invitedBy = invitedBy;
        member.updatedBy = invitedBy;
        return member;
    }

    private static WorkspaceMember base(Workspace workspace, UUID userId, String email, String displayName) {
        WorkspaceMember member = new WorkspaceMember();
        member.workspace = workspace;
        member.userId = userId;
        member.email = email;
        member.displayName = displayName;
        member.status = MemberStatus.ACTIVE;
        member.joinedAt = Instant.now();
        return member;
    }

    public void assignRole(WorkspaceRole newRole, UUID actorId) {
        this.role = newRole;
        this.updatedBy = actorId;
    }

    public boolean isPinned() {
        return pinnedAt != null;
    }

    public void pin() {
        if (pinnedAt == null) {
            this.pinnedAt = Instant.now();
        }
    }

    public void unpin() {
        this.pinnedAt = null;
    }

    public void recordAccess(Instant at) {
        this.lastAccessedAt = at;
    }

    public void suspend(UUID actorId) {
        this.status = MemberStatus.SUSPENDED;
        this.updatedBy = actorId;
    }

    public void reactivate(UUID actorId) {
        this.status = MemberStatus.ACTIVE;
        this.updatedBy = actorId;
    }
}
