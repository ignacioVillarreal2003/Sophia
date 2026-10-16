package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.WorkspaceMemberMapper;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.AddMemberRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateMemberRoleRequest;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceMemberResponse;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.event.MembershipEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.client.UserDirectoryClient;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Membership rules: ADMINs add members; the owner (Workspace.ownerId) may set any role and
 * suspend/reactivate; ADMINs may only toggle other members between EDITOR and VIEWER.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceMemberService {

    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceAccessCache accessCache;
    private final DomainEventPublisher eventPublisher;
    private final WorkspaceMemberMapper memberMapper;
    private final UserDirectoryClient userDirectory;

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceMemberResponse> list(AuthenticatedUser user, UUID workspaceId,
                                                      Pageable pageable) {
        authorization.requireActiveMember(workspaceId, user);
        return PageResponse.from(
                memberRepository.findAllByWorkspaceId(workspaceId, pageable),
                memberMapper::toResponse);
    }

    @Transactional
    public WorkspaceMemberResponse add(AuthenticatedUser user, UUID workspaceId, AddMemberRequest request) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        UserDirectoryClient.UserSummary target = userDirectory.findByEmail(request.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException("No user with that email"));
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, target.id())) {
            throw new ConflictException("That user is already a member of this workspace");
        }

        WorkspaceMember member = memberRepository.save(WorkspaceMember.join(
                workspace, target.id(), target.email(), target.fullName(), request.role(), user.id()));
        eventPublisher.publish(MembershipEvents.MemberAdded.of(
                workspaceId, user.id(), member.getId(), member.getUserId(), member.getRole()));
        accessCache.evict(workspaceId, member.getUserId());
        return memberMapper.toResponse(member);
    }

    @Transactional
    public WorkspaceMemberResponse changeRole(AuthenticatedUser user, UUID workspaceId, UUID memberId,
                                              UpdateMemberRoleRequest request) {
        WorkspaceAccessSnapshot access =
                authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        WorkspaceMember member = loadMember(workspaceId, memberId);
        ensureNotOwner(access, member, "The owner's role cannot be changed");
        ensureNotSelf(user, member, "You cannot change your own role");

        if (!access.isOwner()) {
            boolean swapsEditorViewer = isEditorOrViewer(member.getRole()) && isEditorOrViewer(request.role());
            if (!swapsEditorViewer) {
                throw new ForbiddenOperationException(ErrorCode.ACCESS_DENIED,
                        "Admins can only switch members between EDITOR and VIEWER; only the owner can do more");
            }
        }

        WorkspaceRole previousRole = member.getRole();
        if (previousRole != request.role()) {
            member.assignRole(request.role(), user.id());
            eventPublisher.publish(MembershipEvents.MemberRoleChanged.of(
                    workspaceId, user.id(), member.getId(), member.getUserId(), previousRole, request.role()));
            accessCache.evict(workspaceId, member.getUserId());
        }
        return memberMapper.toResponse(member);
    }

    @Transactional
    public WorkspaceMemberResponse suspend(AuthenticatedUser user, UUID workspaceId, UUID memberId) {
        WorkspaceAccessSnapshot access = authorization.requireOwner(workspaceId, user);
        WorkspaceMember member = loadMember(workspaceId, memberId);
        ensureNotOwner(access, member, "The workspace owner cannot be suspended");
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new ConflictException("Member is already suspended");
        }

        member.suspend(user.id());
        eventPublisher.publish(MembershipEvents.MemberSuspended.of(
                workspaceId, user.id(), member.getId(), member.getUserId()));
        accessCache.evict(workspaceId, member.getUserId());
        return memberMapper.toResponse(member);
    }

    @Transactional
    public WorkspaceMemberResponse reactivate(AuthenticatedUser user, UUID workspaceId, UUID memberId) {
        authorization.requireOwner(workspaceId, user);
        WorkspaceMember member = loadMember(workspaceId, memberId);
        if (member.getStatus() == MemberStatus.ACTIVE) {
            throw new ConflictException("Member is not suspended");
        }

        member.reactivate(user.id());
        eventPublisher.publish(MembershipEvents.MemberReactivated.of(
                workspaceId, user.id(), member.getId(), member.getUserId()));
        accessCache.evict(workspaceId, member.getUserId());
        return memberMapper.toResponse(member);
    }

    @Transactional
    public void remove(AuthenticatedUser user, UUID workspaceId, UUID memberId) {
        WorkspaceAccessSnapshot access =
                authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        WorkspaceMember member = loadMember(workspaceId, memberId);
        ensureNotOwner(access, member, "The workspace owner cannot be removed");
        ensureNotSelf(user, member, "Use the leave operation to remove yourself");
        if (!access.isOwner() && member.getRole() == WorkspaceRole.ADMIN) {
            throw new ForbiddenOperationException(ErrorCode.ACCESS_DENIED,
                    "Only the owner can remove an admin");
        }

        memberRepository.delete(member);
        eventPublisher.publish(MembershipEvents.MemberRemoved.of(
                workspaceId, user.id(), member.getId(), member.getUserId(), false));
        accessCache.evict(workspaceId, member.getUserId());
    }

    @Transactional
    public void leave(AuthenticatedUser user, UUID workspaceId) {
        WorkspaceAccessSnapshot access = authorization.requireActiveMember(workspaceId, user);
        if (access.isOwner()) {
            throw new ConflictException(ErrorCode.OWNERSHIP_TRANSFER_REQUIRED,
                    "Transfer ownership before leaving the workspace");
        }
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.id())
                .orElseThrow(() -> new ForbiddenOperationException(ErrorCode.MEMBERSHIP_REQUIRED,
                        "You are not a member of this workspace"));

        memberRepository.delete(member);
        eventPublisher.publish(MembershipEvents.MemberRemoved.of(
                workspaceId, user.id(), member.getId(), user.id(), true));
        accessCache.evict(workspaceId, user.id());
    }

    private WorkspaceMember loadMember(UUID workspaceId, UUID memberId) {
        return memberRepository.findByIdAndWorkspaceId(memberId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member", memberId));
    }

    private static boolean isEditorOrViewer(WorkspaceRole role) {
        return role == WorkspaceRole.EDITOR || role == WorkspaceRole.VIEWER;
    }

    private void ensureNotOwner(WorkspaceAccessSnapshot access, WorkspaceMember member, String message) {
        if (member.getUserId().equals(access.ownerId())) {
            throw new ConflictException(ErrorCode.OWNERSHIP_TRANSFER_REQUIRED, message);
        }
    }

    private void ensureNotSelf(AuthenticatedUser user, WorkspaceMember member, String message) {
        if (member.getUserId().equals(user.id())) {
            throw new BadRequestException(message);
        }
    }
}
