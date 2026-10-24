package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.AddMemberRequest;
import com.sophia.documentworkspace.domain.dto.response.FacetOptionResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceInvitationResponse;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceInvitation;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.InvitationStatus;
import com.sophia.documentworkspace.domain.entity.enums.NotificationType;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.event.MembershipEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.client.UserDirectoryClient;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.InvitationSearch;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceInvitationRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Joining a workspace takes two people: an admin invites an existing user with a role, and that user accepts
 * (becoming a member) or declines. Invitations expire, and an admin can withdraw one that is still open.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceInvitationService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_FACETS = 20;

    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceAccessCache accessCache;
    private final UserDirectoryClient userDirectory;
    private final DomainEventPublisher eventPublisher;
    private final WorkspaceNotifier notifier;
    private final InvitationSearch search;

    /** How the invitations received are narrowed and ordered; every field is optional. */
    public record Query(String text, WorkspaceRole role, String invitedBy, LocalDate from, LocalDate to,
                        InvitationSearch.Order order, boolean desc) {
    }

    @Transactional
    public WorkspaceInvitationResponse invite(AuthenticatedUser user, UUID workspaceId, AddMemberRequest request) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!workspace.isActive()) {
            throw new ConflictException("This workspace is not accepting new people right now");
        }
        UserDirectoryClient.UserSummary target = userDirectory.findByEmail(request.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException("No user with that email"));
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, target.id())) {
            throw new ConflictException("That user is already a member of this workspace");
        }
        var open = invitations.findFirstByWorkspaceIdAndInviteeUserIdAndStatus(
                workspaceId, target.id(), InvitationStatus.PENDING);
        if (open.isPresent()) {
            if (open.get().isOpen()) {
                throw new ConflictException("That user already has a pending invitation");
            }
            open.get().answer(InvitationStatus.REVOKED); // an expired one makes room for the new offer
            invitations.saveAndFlush(open.get());
        }

        WorkspaceInvitation invitation = invitations.save(WorkspaceInvitation.create(
                workspaceId, target.id(), target.email(), target.fullName(), request.role(), user.id(), user.email()));
        notifier.notify(NotificationType.INVITATION, target.id(), user.id(),
                "Invitation to " + workspace.getName(),
                user.email() + " invited you to join as " + request.role().name().toLowerCase(), workspaceId);
        return toResponse(invitation, workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> pendingFor(AuthenticatedUser user, UUID workspaceId) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        return invitations.findAllByWorkspaceIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        workspaceId, InvitationStatus.PENDING, Instant.now())
                .stream().map(i -> toResponse(i, workspace)).toList();
    }

    @Transactional
    public void revoke(AuthenticatedUser user, UUID workspaceId, UUID invitationId) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        WorkspaceInvitation invitation = invitations.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        requirePending(invitation);
        invitation.answer(InvitationStatus.REVOKED);
    }

    @Transactional
    public WorkspaceInvitationResponse changeRole(AuthenticatedUser user, UUID workspaceId, UUID invitationId, WorkspaceRole role) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        WorkspaceInvitation invitation = invitations.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        requirePending(invitation);
        invitation.changeRole(role);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        return toResponse(invitation, workspace);
    }

    /** What the signed-in person has been invited to and has not answered yet, searched, filtered and paged. */
    @Transactional(readOnly = true)
    public PageResponse<WorkspaceInvitationResponse> mine(AuthenticatedUser user, Query query, int page, int size) {
        var filter = new InvitationSearch.Filter(query.text(), query.role(), query.invitedBy(),
                query.from() == null ? null : query.from().atStartOfDay().toInstant(ZoneOffset.UTC),
                // The last day is included, so the range ends where the next one starts.
                query.to() == null ? null : query.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        var found = search.open(user.id(), Instant.now(), filter, query.order(), query.desc(), pageable);
        return PageResponse.from(found, row -> toResponse((WorkspaceInvitation) row[0], (Workspace) row[1]));
    }

    /** The people who invited the caller, matching the text, to pick one in a filter. */
    @Transactional(readOnly = true)
    public List<FacetOptionResponse> inviters(AuthenticatedUser user, String text) {
        return search.inviters(user.id(), Instant.now(), text, MAX_FACETS).stream()
                .map(email -> new FacetOptionResponse(email, email)).toList();
    }

    @Transactional
    public WorkspaceInvitationResponse accept(AuthenticatedUser user, UUID invitationId) {
        WorkspaceInvitation invitation = loadOwn(user, invitationId);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(invitation.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", invitation.getWorkspaceId()));
        if (!workspace.isActive()) {
            throw new ConflictException("This workspace is not accepting new people right now");
        }
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), user.id())) {
            WorkspaceMember member = memberRepository.save(WorkspaceMember.join(
                    workspace, user.id(), invitation.getInviteeEmail(), invitation.getInviteeName(),
                    invitation.getRole(), invitation.getInvitedBy()));
            eventPublisher.publish(MembershipEvents.MemberAdded.of(
                    workspace.getId(), invitation.getInvitedBy(), member.getId(), user.id(), invitation.getRole()));
            accessCache.evict(workspace.getId(), user.id());
        }
        invitation.answer(InvitationStatus.ACCEPTED);
        notifier.notify(invitation.getInvitedBy(), user.id(), "Invitation accepted",
                invitation.getInviteeEmail() + " joined " + workspace.getName(), workspace.getId());
        return toResponse(invitation, workspace);
    }

    @Transactional
    public void decline(AuthenticatedUser user, UUID invitationId) {
        WorkspaceInvitation invitation = loadOwn(user, invitationId);
        invitation.answer(InvitationStatus.DECLINED);
        String name = workspaceRepository.findById(invitation.getWorkspaceId())
                .map(Workspace::getName).orElse("the workspace");
        notifier.notify(invitation.getInvitedBy(), user.id(), "Invitation declined",
                invitation.getInviteeEmail() + " declined to join " + name, invitation.getWorkspaceId());
    }

    private WorkspaceInvitation loadOwn(AuthenticatedUser user, UUID invitationId) {
        // Someone else's invitation looks the same as one that does not exist.
        WorkspaceInvitation invitation = invitations.findByIdAndInviteeUserId(invitationId, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId));
        requirePending(invitation);
        if (!invitation.isOpen()) {
            throw new BadRequestException("This invitation has expired; ask for a new one");
        }
        return invitation;
    }

    private static void requirePending(WorkspaceInvitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("This invitation was already answered");
        }
    }

    private static WorkspaceInvitationResponse toResponse(WorkspaceInvitation i, Workspace workspace) {
        return new WorkspaceInvitationResponse(i.getId(), i.getWorkspaceId(), workspace.getName(),
                workspace.getColor(), i.getInviteeEmail(), i.getInviteeName(), i.getRole(), i.getInvitedByEmail(),
                i.getStatus(), i.getCreatedAt(), i.getExpiresAt());
    }
}
