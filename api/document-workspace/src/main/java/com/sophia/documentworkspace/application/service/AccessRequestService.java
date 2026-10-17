package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.response.AccessRequestResponse;
import com.sophia.documentworkspace.domain.dto.response.AccessPreviewResponse;
import com.sophia.documentworkspace.domain.entity.AccessRequest;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.event.MembershipEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.client.UserDirectoryClient;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.AccessRequestRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * People who follow a link to a workspace they do not belong to can ask its admins for access. The admins
 * hear about it, and approving a request adds the person with the role the admin picks.
 */
@Service
@RequiredArgsConstructor
public class AccessRequestService {

    /** After a denial the same person has to wait before asking again. */
    static final Duration COOLDOWN_AFTER_DENIAL = Duration.ofHours(24);

    private final AccessRequestRepository requestRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceAccessCache accessCache;
    private final UserDirectoryClient userDirectory;
    private final DomainEventPublisher eventPublisher;
    private final WorkspaceNotifier notifier;

    /** What a signed-in person sees when they open a workspace they cannot enter yet. */
    @Transactional(readOnly = true)
    public AccessPreviewResponse preview(AuthenticatedUser user, UUID workspaceId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        boolean member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.id())
                .map(m -> m.getStatus() == MemberStatus.ACTIVE).orElse(false);
        var latest = requestRepository.findFirstByWorkspaceIdAndUserIdOrderByCreatedAtDesc(workspaceId, user.id());
        return new AccessPreviewResponse(workspace.getId(), workspace.getName(), workspace.getColor(), member,
                latest.map(AccessRequest::getStatus).orElse(null),
                latest.map(AccessRequest::getCreatedAt).orElse(null));
    }

    @Transactional
    public AccessRequestResponse request(AuthenticatedUser user, UUID workspaceId, String message) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!workspace.isActive()) {
            throw new ConflictException("This workspace is not accepting new people right now");
        }
        var existingMember = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.id());
        if (existingMember.isPresent()) {
            throw new ConflictException(existingMember.get().getStatus() == MemberStatus.SUSPENDED
                    ? "Your access to this workspace is suspended; contact its owner"
                    : "You are already a member of this workspace");
        }
        if (requestRepository.findFirstByWorkspaceIdAndUserIdAndStatus(workspaceId, user.id(), RequestStatus.PENDING)
                .isPresent()) {
            throw new ConflictException("You already asked for access; the admins were notified");
        }
        requestRepository.findFirstByWorkspaceIdAndUserIdOrderByCreatedAtDesc(workspaceId, user.id())
                .filter(last -> last.getStatus() == RequestStatus.DENIED && last.getDecidedAt() != null
                        && last.getDecidedAt().isAfter(Instant.now().minus(COOLDOWN_AFTER_DENIAL)))
                .ifPresent(last -> {
                    throw new ConflictException("Your last request was declined; you can ask again in a day");
                });

        AccessRequest request = requestRepository.save(
                AccessRequest.create(workspaceId, user.id(), user.email(), message));
        for (WorkspaceMember admin : memberRepository.findAllByWorkspaceIdAndRoleAndStatus(
                workspaceId, WorkspaceRole.ADMIN, MemberStatus.ACTIVE)) {
            notifier.notify(admin.getUserId(), user.id(), "Access request",
                    user.email() + " asked to join " + workspace.getName(), workspaceId);
        }
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> pending(AuthenticatedUser user, UUID workspaceId) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        return requestRepository.findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, RequestStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccessRequestResponse approve(AuthenticatedUser user, UUID workspaceId, UUID requestId, WorkspaceRole role) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        AccessRequest request = loadPending(workspaceId, requestId);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.getUserId())) {
            request.decide(RequestStatus.APPROVED, user.id());
            return toResponse(request);
        }

        UserDirectoryClient.UserSummary person = userDirectory.findByEmail(request.getEmail())
                .filter(p -> request.getUserId().equals(p.id()))
                .orElseThrow(() -> new BadRequestException("That account no longer exists or is not active"));
        WorkspaceMember member = memberRepository.save(WorkspaceMember.join(
                workspace, person.id(), person.email(), person.fullName(), role, user.id()));
        request.decide(RequestStatus.APPROVED, user.id());

        eventPublisher.publish(MembershipEvents.MemberAdded.of(
                workspaceId, user.id(), member.getId(), person.id(), role));
        accessCache.evict(workspaceId, person.id());
        notifier.notify(person.id(), user.id(), "Access granted",
                "You can now open " + workspace.getName() + " as " + role.name().toLowerCase(), workspaceId);
        return toResponse(request);
    }

    @Transactional
    public AccessRequestResponse deny(AuthenticatedUser user, UUID workspaceId, UUID requestId) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.MANAGE_MEMBERS);
        AccessRequest request = loadPending(workspaceId, requestId);
        request.decide(RequestStatus.DENIED, user.id());
        String name = workspaceRepository.findById(workspaceId).map(Workspace::getName).orElse("the workspace");
        notifier.notify(request.getUserId(), user.id(), "Access declined",
                "Your request to join " + name + " was declined", workspaceId);
        return toResponse(request);
    }

    private AccessRequest loadPending(UUID workspaceId, UUID requestId) {
        AccessRequest request = requestRepository.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Access request", requestId));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("This request was already answered");
        }
        return request;
    }

    private AccessRequestResponse toResponse(AccessRequest request) {
        return new AccessRequestResponse(request.getId(), request.getWorkspaceId(), request.getUserId(),
                request.getEmail(), request.getMessage(), request.getStatus(), request.getCreatedAt(),
                request.getDecidedAt());
    }
}
