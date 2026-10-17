package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.StorageRequestResponse;
import com.sophia.documentworkspace.domain.entity.StorageRequest;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import com.sophia.documentworkspace.domain.event.WorkspaceEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.StorageRequestRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A workspace owner who runs out of space asks the platform administrators for more. Approving adds the granted
 * megabytes to the quota; either way the owner is told.
 */
@Service
@RequiredArgsConstructor
public class StorageRequestService {

    static final long MAX_REQUEST_MB = 1_048_576L;

    private final StorageRequestRepository requestRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceLimitsRepository limitsRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ArtifactRepository artifactRepository;
    private final StorageQuotaService quotaService;
    private final WorkspaceNotifier notifier;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public StorageRequestResponse request(AuthenticatedUser user, UUID workspaceId, long requestedMb, String message) {
        Workspace workspace = load(workspaceId);
        requireOwner(user, workspace);
        if (requestedMb < 1 || requestedMb > MAX_REQUEST_MB) {
            throw new BadRequestException("Ask for between 1 MB and %d MB".formatted(MAX_REQUEST_MB));
        }
        Long quota = limitsRepository.findById(workspaceId).map(WorkspaceLimits::getStorageQuotaMb).orElse(null);
        if (quota == null) {
            throw new ConflictException("This workspace has no storage limit");
        }
        if (requestRepository.findFirstByWorkspaceIdAndStatus(workspaceId, RequestStatus.PENDING).isPresent()) {
            throw new ConflictException("There is already a pending storage request for this workspace");
        }
        StorageRequest saved = requestRepository.save(
                StorageRequest.create(workspaceId, user.id(), requestedMb, message));
        return toResponse(saved, workspace, user.email());
    }

    /** The latest request of a workspace, for its owner; null when nobody ever asked. */
    @Transactional(readOnly = true)
    public StorageRequestResponse latest(AuthenticatedUser user, UUID workspaceId) {
        Workspace workspace = load(workspaceId);
        requireOwner(user, workspace);
        return requestRepository.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .map(r -> toResponse(r, workspace, null)).orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<StorageRequestResponse> list(AuthenticatedUser user, RequestStatus status, Pageable pageable) {
        requireManage(user);
        return PageResponse.from(requestRepository.findAllByStatusOrderByCreatedAtAsc(
                status == null ? RequestStatus.PENDING : status, pageable), r -> toResponse(r, null, null));
    }

    @Transactional(readOnly = true)
    public long pendingCount(AuthenticatedUser user) {
        requireManage(user);
        return requestRepository.countByStatus(RequestStatus.PENDING);
    }

    @Transactional
    public StorageRequestResponse approve(AuthenticatedUser user, UUID requestId, Long grantedMb) {
        requireManage(user);
        StorageRequest request = loadPending(requestId);
        long granted = grantedMb == null ? request.getRequestedMb() : grantedMb;
        if (granted < 1 || granted > MAX_REQUEST_MB) {
            throw new BadRequestException("Grant between 1 MB and %d MB".formatted(MAX_REQUEST_MB));
        }
        Workspace workspace = load(request.getWorkspaceId());
        WorkspaceLimits limits = limitsRepository.findById(workspace.getId())
                .orElseThrow(() -> new ConflictException("This workspace has no storage limit"));
        if (limits.getStorageQuotaMb() == null) {
            throw new ConflictException("This workspace has no storage limit");
        }
        limits.changeStorageQuota(limits.getStorageQuotaMb() + granted, user.id());
        request.approve(user.id(), granted);
        quotaService.evaluate(workspace.getId(), 0);
        eventPublisher.publish(WorkspaceEvents.WorkspaceLimitsUpdated.of(
                workspace.getId(), user.id(), limits.getStorageQuotaMb()));
        notifier.notify(request.getRequestedBy(), user.id(), "Storage increased",
                workspace.getName() + " now has " + granted + " MB more storage.", workspace.getId());
        return toResponse(request, workspace, null);
    }

    @Transactional
    public StorageRequestResponse deny(AuthenticatedUser user, UUID requestId) {
        requireManage(user);
        StorageRequest request = loadPending(requestId);
        Workspace workspace = load(request.getWorkspaceId());
        request.deny(user.id());
        notifier.notify(request.getRequestedBy(), user.id(), "Storage request declined",
                "Your request for more storage in " + workspace.getName() + " was declined.", workspace.getId());
        return toResponse(request, workspace, null);
    }

    private StorageRequest loadPending(UUID requestId) {
        StorageRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage request", requestId));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("This request was already answered");
        }
        return request;
    }

    private Workspace load(UUID workspaceId) {
        return workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    private void requireOwner(AuthenticatedUser user, Workspace workspace) {
        if (!workspace.getOwnerId().equals(user.id())) {
            throw new ForbiddenOperationException("Only the workspace owner can ask for more storage");
        }
    }

    private void requireManage(AuthenticatedUser user) {
        if (!user.hasPermission(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE)) {
            throw new ForbiddenOperationException("You do not have permission to manage workspaces");
        }
    }

    private StorageRequestResponse toResponse(StorageRequest request, Workspace knownWorkspace, String knownEmail) {
        Workspace workspace = knownWorkspace != null ? knownWorkspace : load(request.getWorkspaceId());
        String email = knownEmail != null ? knownEmail : memberRepository
                .findByWorkspaceIdAndUserId(workspace.getId(), request.getRequestedBy())
                .map(WorkspaceMember::getEmail).orElse(null);
        Long quota = limitsRepository.findById(workspace.getId()).map(WorkspaceLimits::getStorageQuotaMb).orElse(null);
        return new StorageRequestResponse(request.getId(), workspace.getId(), workspace.getName(),
                request.getRequestedBy(), email, request.getRequestedMb(), request.getMessage(), request.getStatus(),
                request.getGrantedMb(), request.getCreatedAt(), request.getHandledAt(),
                artifactRepository.sumFileSizes(workspace.getId()), quota);
    }
}
