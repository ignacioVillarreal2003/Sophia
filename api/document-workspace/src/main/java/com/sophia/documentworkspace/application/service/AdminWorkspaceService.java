package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceLimitsRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceStatusRequest;
import com.sophia.documentworkspace.domain.dto.response.AdminWorkspaceDetailResponse;
import com.sophia.documentworkspace.domain.dto.response.AdminWorkspaceResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.domain.event.WorkspaceEvents;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import com.sophia.documentworkspace.infrastructure.repository.specification.WorkspaceSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Platform-wide workspace administration: status and limits, for users holding WORKSPACE_MANAGE. */
@Service
@RequiredArgsConstructor
public class AdminWorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceLimitsRepository limitsRepository;
    private final ArtifactRepository artifactRepository;
    private final WorkspaceAccessCache accessCache;
    private final DomainEventPublisher eventPublisher;
    private final StorageQuotaService quotaService;

    @Transactional(readOnly = true)
    public PageResponse<AdminWorkspaceResponse> list(AuthenticatedUser user, String search, WorkspaceStatus status,
                                                     Pageable pageable) {
        requireManage(user);
        String normalized = search == null || search.isBlank() ? null : search.trim();
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name").ascending());
        Page<Workspace> page = workspaceRepository.findAll(WorkspaceSpecifications.all(normalized, status), effective);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AdminWorkspaceDetailResponse detail(AuthenticatedUser user, UUID workspaceId) {
        requireManage(user);
        Workspace workspace = load(workspaceId);
        var members = memberRepository.findAllByWorkspaceId(workspaceId, Pageable.unpaged()).stream()
                .map(m -> new AdminWorkspaceDetailResponse.Member(m.getUserId(), m.getEmail(), m.getDisplayName(),
                        m.getRole(), m.getStatus(), m.getJoinedAt()))
                .toList();
        var stats = new AdminWorkspaceDetailResponse.Stats(
                artifactRepository.countByWorkspaceIdAndDeletedAtIsNull(workspaceId),
                artifactRepository.countByWorkspaceIdAndDeletedAtIsNotNull(workspaceId));
        return new AdminWorkspaceDetailResponse(toResponse(workspace), workspace.getDescription(), workspace.getLanguage(),
                workspace.getTimezone(), workspace.getDateFormat(), workspace.getTrashRetentionDays(),
                workspace.isPublicSharingEnabled(), workspace.getUpdatedBy(), stats, members);
    }

    @Transactional
    public AdminWorkspaceResponse changeStatus(AuthenticatedUser user, UUID workspaceId, UpdateWorkspaceStatusRequest request) {
        requireManage(user);
        Workspace workspace = load(workspaceId);
        WorkspaceStatus before = workspace.getStatus();
        if (before != request.status()) {
            workspace.changeStatus(request.status());
            workspace.markUpdatedBy(user.id());
            accessCache.evictWorkspace(workspaceId);
            eventPublisher.publish(WorkspaceEvents.WorkspaceStatusChanged.of(
                    workspaceId, user.id(), before.name(), request.status().name()));
        }
        return toResponse(workspace);
    }

    @Transactional
    public AdminWorkspaceResponse changeLimits(AuthenticatedUser user, UUID workspaceId, UpdateWorkspaceLimitsRequest request) {
        requireManage(user);
        Workspace workspace = load(workspaceId);
        WorkspaceLimits limits = limitsRepository.findById(workspaceId).orElseGet(() -> WorkspaceLimits.forWorkspace(workspaceId));
        limits.changeStorageQuota(request.storageQuotaMb(), user.id());
        limitsRepository.save(limits);
        quotaService.evaluate(workspaceId, 0);
        workspace.markUpdatedBy(user.id());
        eventPublisher.publish(WorkspaceEvents.WorkspaceLimitsUpdated.of(workspaceId, user.id(), limits.getStorageQuotaMb()));
        return toResponse(workspace);
    }

    private AdminWorkspaceResponse toResponse(Workspace workspace) {
        UUID id = workspace.getId();
        String ownerEmail = memberRepository.findByWorkspaceIdAndUserId(id, workspace.getOwnerId())
                .map(WorkspaceMember::getEmail)
                .orElse(null);
        Long quota = limitsRepository.findById(id).map(WorkspaceLimits::getStorageQuotaMb).orElse(null);
        String logo = workspace.getLogoUrl() == null || workspace.getLogoUrl().isBlank()
                ? null
                : "/api/v1/workspaces/" + id + "/logo?v=" + workspace.getLogoUrl().substring(workspace.getLogoUrl().lastIndexOf('/') + 1);
        return new AdminWorkspaceResponse(id, workspace.getName(), workspace.getColor(), logo,
                workspace.getOwnerId(), ownerEmail, workspace.getStatus(), memberRepository.countByWorkspaceId(id),
                artifactRepository.sumFileSizes(id), quota, workspace.getCreatedAt(), workspace.getUpdatedAt());
    }

    private Workspace load(UUID workspaceId) {
        return workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    private void requireManage(AuthenticatedUser user) {
        if (!user.hasPermission(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE)) {
            throw new ForbiddenOperationException("You don't have permission to manage workspaces");
        }
    }
}
