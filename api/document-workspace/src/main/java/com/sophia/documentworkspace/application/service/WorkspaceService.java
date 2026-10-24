package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.WorkspaceMapper;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.TransferOwnershipRequest;
import com.sophia.documentworkspace.domain.dto.request.CreateWorkspaceRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceSettingsRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceRequest;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.dto.request.WorkspaceListFilter;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLabelRepository;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceUsageResponse;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import org.springframework.web.multipart.MultipartFile;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.domain.event.MembershipEvents;
import com.sophia.documentworkspace.domain.event.WorkspaceEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final String DEFAULT_LANGUAGE = "en";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceAccessCache accessCache;
    private final DomainEventPublisher eventPublisher;
    private final TrashService trashService;
    private final StorageService storageService;
    private final ArtifactRepository artifactRepository;
    private final WorkspaceLimitsRepository limitsRepository;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceListEnricher listEnricher;
    private final WorkspaceStarterService starterService;
    private final WorkspaceLabelRepository labelRepository;
    private final ApplicationProperties properties;
    private final UserWorkspacePreferencesService preferences;

    @org.springframework.beans.factory.annotation.Value("${sophia.workspaces.default-storage-quota-mb:5120}")
    private long defaultStorageQuotaMb;

    @Transactional
    public WorkspaceResponse create(AuthenticatedUser user, CreateWorkspaceRequest request) {
        Workspace workspace = Workspace.create(
                request.name(),
                request.description(),
                null,
                request.color(),
                request.language() == null ? DEFAULT_LANGUAGE : request.language(),
                user.id());
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember owner = memberRepository.save(
                WorkspaceMember.ownerOf(workspace, user.id(), user.email(), null));
        WorkspaceLimits limits = WorkspaceLimits.forWorkspace(workspace.getId());
        limits.changeStorageQuota(defaultStorageQuotaMb, null);
        limitsRepository.save(limits);

        starterService.apply(workspace, user.id(), request.template());

        eventPublisher.publish(WorkspaceEvents.WorkspaceCreated.of(
                workspace.getId(), user.id(), workspace.getName()));
        eventPublisher.publish(MembershipEvents.MemberAdded.of(
                workspace.getId(), user.id(), owner.getId(), user.id(), WorkspaceRole.ADMIN));

        return workspaceMapper.toResponse(workspace, WorkspaceRole.ADMIN);
    }

    private static final Duration ACCESS_THROTTLE = Duration.ofMinutes(5);

    @Transactional
    public WorkspaceResponse getById(AuthenticatedUser user, UUID workspaceId) {
        WorkspaceAccessSnapshot access = authorization.requireActiveMember(workspaceId, user);
        Workspace workspace = loadActive(workspaceId);
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.id()).orElse(null);
        Instant now = Instant.now();
        if (member != null && (member.getLastAccessedAt() == null
                || member.getLastAccessedAt().isBefore(now.minus(ACCESS_THROTTLE)))) {
            member.recordAccess(now);
        }
        Map<UUID, WorkspaceMember> memberships = member == null ? Map.of() : Map.of(workspaceId, member);
        return listEnricher.enrich(user.id(), List.of(workspaceMapper.toResponse(workspace, access.role())),
                memberships, false).get(0);
    }

    @Transactional
    public WorkspaceResponse pin(AuthenticatedUser user, UUID workspaceId, boolean pinned) {
        WorkspaceAccessSnapshot access = authorization.requireActiveMember(workspaceId, user);
        Workspace workspace = loadActive(workspaceId);
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member", user.id()));
        if (pinned) {
            member.pin();
        } else {
            member.unpin();
        }
        return listEnricher.enrich(user.id(), List.of(workspaceMapper.toResponse(workspace, access.role())),
                Map.of(workspaceId, member), false).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceResponse> listMine(AuthenticatedUser user, WorkspaceListFilter filter,
                                                     int page, int size, boolean withStats) {
        WorkspaceListFilter normalized = new WorkspaceListFilter(
                filter.search() == null || filter.search().isBlank() ? null : filter.search().trim(),
                filter.role(), filter.status(), filter.ownership(), filter.pinnedOnly(),
                filter.labelId(), filter.createdFrom(), filter.createdTo(), filter.activeFrom(),
                filter.activeTo(), filter.sort(), filter.descending());
        if (normalized.labelId() != null
                && labelRepository.findByIdAndUserId(normalized.labelId(), user.id()).isEmpty()) {
            throw new ResourceNotFoundException("Label", normalized.labelId());
        }
        // The specification orders the rows itself, so the page request carries no sort.
        Page<Workspace> result = workspaceRepository.findAll(
                WorkspaceSpecifications.forMember(user.id(), MemberStatus.ACTIVE, normalized),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<UUID, WorkspaceMember> memberships = memberRepository
                .findAllByUserIdAndWorkspaceIdIn(user.id(), result.getContent().stream().map(Workspace::getId).toList())
                .stream()
                .collect(Collectors.toMap(m -> m.getWorkspace().getId(), Function.identity()));
        List<WorkspaceResponse> base = result.getContent().stream()
                .map(workspace -> workspaceMapper.toResponse(workspace, memberships.get(workspace.getId()).getRole()))
                .toList();
        List<WorkspaceResponse> enriched = listEnricher.enrich(user.id(), base, memberships, withStats);
        return new PageResponse<>(enriched, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceResponse> listDeleted(AuthenticatedUser user, Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("deletedAt").descending());
        return PageResponse.from(workspaceRepository
                .findAllByOwnerIdAndDeletedAtIsNotNull(user.id(), effectivePageable)
                .map((Function<Workspace, WorkspaceResponse>) workspace ->
                        workspaceMapper.toResponse(workspace, WorkspaceRole.ADMIN)
                                .withPurgeAt(purgeDate(workspace))));
    }

    /** When a trashed workspace will be erased for good. */
    public Instant purgeDate(Workspace workspace) {
        return workspace.getDeletedAt() == null ? null
                : workspace.getDeletedAt().plus(Duration.ofDays(preferences.trashRetentionDays(workspace.getOwnerId())));
    }

    // ---- logo ----------------------------------------------------------------------------------

    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> LOGO_TYPES = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "image/gif", "gif");

    /** Image bytes plus the content type derived from the stored key. */
    public record LogoContent(byte[] bytes, String contentType) {
    }

    @Transactional
    public WorkspaceResponse uploadLogo(AuthenticatedUser user, UUID workspaceId, MultipartFile file) {
        WorkspaceAccessSnapshot access = authorization.requirePermission(
                workspaceId, user, WorkspacePermission.MANAGE_WORKSPACE);
        Workspace workspace = loadActive(workspaceId);

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = LOGO_TYPES.get(contentType);
        if (extension == null) {
            throw new BadRequestException("The logo must be a PNG, JPEG, WebP or GIF image");
        }
        if (file.isEmpty() || file.getSize() > MAX_LOGO_BYTES) {
            throw new BadRequestException("The logo must be smaller than 2 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new BadRequestException("The logo could not be read");
        }

        String previous = workspace.getLogoUrl();
        String key = "workspaces/%s/logo/%s.%s".formatted(workspaceId, UUID.randomUUID(), extension);
        storageService.putObject(key, bytes, contentType);
        workspace.changeLogo(key);
        workspace.markUpdatedBy(user.id());
        if (previous != null && !previous.isBlank()) {
            storageService.deleteObjectQuietly(previous);
        }
        eventPublisher.publish(WorkspaceEvents.WorkspaceUpdated.of(workspaceId, user.id(),
                new WorkspaceEvents.WorkspaceUpdated.Snapshot(workspace.getName(), workspace.getDescription(),
                        previous, workspace.getColor(), workspace.getLanguage())));
        return workspaceMapper.toResponse(workspace, access.role());
    }

    @Transactional
    public WorkspaceResponse deleteLogo(AuthenticatedUser user, UUID workspaceId) {
        WorkspaceAccessSnapshot access = authorization.requirePermission(
                workspaceId, user, WorkspacePermission.MANAGE_WORKSPACE);
        Workspace workspace = loadActive(workspaceId);
        String previous = workspace.getLogoUrl();
        if (previous != null && !previous.isBlank()) {
            workspace.changeLogo(null);
            workspace.markUpdatedBy(user.id());
            storageService.deleteObjectQuietly(previous);
            eventPublisher.publish(WorkspaceEvents.WorkspaceUpdated.of(workspaceId, user.id(),
                    new WorkspaceEvents.WorkspaceUpdated.Snapshot(workspace.getName(), workspace.getDescription(),
                            previous, workspace.getColor(), workspace.getLanguage())));
        }
        return workspaceMapper.toResponse(workspace, access.role());
    }

    @Transactional(readOnly = true)
    public LogoContent readLogo(AuthenticatedUser user, UUID workspaceId) {
        authorization.requireActiveMember(workspaceId, user);
        Workspace workspace = loadActive(workspaceId);
        String key = workspace.getLogoUrl();
        if (key == null || key.isBlank()) {
            throw new ResourceNotFoundException("Workspace logo", workspaceId);
        }
        String extension = key.substring(key.lastIndexOf('.') + 1);
        String contentType = LOGO_TYPES.entrySet().stream()
                .filter(e -> e.getValue().equals(extension))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("application/octet-stream");
        try (java.io.InputStream in = storageService.getObject(key)) {
            return new LogoContent(in.readAllBytes(), contentType);
        } catch (java.io.IOException e) {
            throw new ResourceNotFoundException("Workspace logo", workspaceId);
        }
    }

    // ---- storage usage ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public WorkspaceUsageResponse usage(AuthenticatedUser user, UUID workspaceId) {
        authorization.requireActiveMember(workspaceId, user);
        Workspace workspace = loadActive(workspaceId);
        Long quotaMb = limitsRepository.findById(workspaceId).map(WorkspaceLimits::getStorageQuotaMb).orElse(null);
        return new WorkspaceUsageResponse(artifactRepository.sumFileSizes(workspaceId), quotaMb);
    }

    @Transactional
    public WorkspaceResponse update(AuthenticatedUser user, UUID workspaceId, UpdateWorkspaceRequest request) {
        WorkspaceAccessSnapshot access = authorization.requirePermission(
                workspaceId, user, WorkspacePermission.MANAGE_WORKSPACE);
        Workspace workspace = loadActive(workspaceId);

        WorkspaceEvents.WorkspaceUpdated.Snapshot before = new WorkspaceEvents.WorkspaceUpdated.Snapshot(
                workspace.getName(), workspace.getDescription(), workspace.getLogoUrl(),
                workspace.getColor(), workspace.getLanguage());
        workspace.updateDetails(request.name(), request.description(), request.color(), request.language());
        workspace.markUpdatedBy(user.id());

        eventPublisher.publish(WorkspaceEvents.WorkspaceUpdated.of(workspaceId, user.id(), before));
        return workspaceMapper.toResponse(workspace, access.role());
    }

    @Transactional
    public WorkspaceResponse updateSettings(AuthenticatedUser user, UUID workspaceId,
                                            UpdateWorkspaceSettingsRequest request) {
        WorkspaceAccessSnapshot access = authorization.requirePermission(
                workspaceId, user, WorkspacePermission.MANAGE_WORKSPACE);
        Workspace workspace = loadActive(workspaceId);
        if (request.timezone() != null) {
            try {
                ZoneId.of(request.timezone());
            } catch (DateTimeException e) {
                throw new BadRequestException("Unknown time zone: " + request.timezone());
            }
        }

        WorkspaceEvents.WorkspaceSettingsUpdated.Snapshot before = new WorkspaceEvents.WorkspaceSettingsUpdated.Snapshot(
                workspace.getTrashRetentionDays(), workspace.isPublicSharingEnabled(),
                workspace.getTimezone(), workspace.getDateFormat());
        workspace.updatePreferences(request.trashRetentionDays(), request.publicSharingEnabled(),
                request.timezone(), request.dateFormat());
        workspace.markUpdatedBy(user.id());

        eventPublisher.publish(WorkspaceEvents.WorkspaceSettingsUpdated.of(workspaceId, user.id(), before));
        return workspaceMapper.toResponse(workspace, access.role());
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID workspaceId) {
        authorization.requireOwner(workspaceId, user);
        Workspace workspace = loadActive(workspaceId);
        workspace.softDelete(user.id());
        eventPublisher.publish(WorkspaceEvents.WorkspaceDeleted.of(workspaceId, user.id()));
        accessCache.evictWorkspace(workspaceId);
    }

    @Transactional
    public WorkspaceResponse restore(AuthenticatedUser user, UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!workspace.isDeleted()) {
            throw new ConflictException("Workspace is not deleted");
        }
        if (!workspace.getOwnerId().equals(user.id())
                && !user.hasPermission(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE)) {
            throw new ForbiddenOperationException("Only the workspace owner can restore it");
        }
        workspace.restore();
        workspace.markUpdatedBy(user.id());
        eventPublisher.publish(WorkspaceEvents.WorkspaceRestored.of(workspaceId, user.id()));
        accessCache.evictWorkspace(workspaceId);
        return workspaceMapper.toResponse(workspace, WorkspaceRole.ADMIN);
    }

    /** Permanently removes a soft-deleted workspace together with all its content (owner only). */
    @Transactional
    public void purge(AuthenticatedUser user, UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!workspace.isDeleted()) {
            throw new ConflictException("Only deleted workspaces can be permanently deleted");
        }
        if (!workspace.getOwnerId().equals(user.id())
                && !user.hasPermission(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE)) {
            throw new ForbiddenOperationException("Only the workspace owner can permanently delete it");
        }
        trashService.purgeWorkspaceContent(workspaceId);
        workspaceRepository.delete(workspace);
        eventPublisher.publish(WorkspaceEvents.WorkspacePurged.of(workspaceId, user.id()));
        accessCache.evictWorkspace(workspaceId);
    }

    @Transactional
    public void transferOwnership(AuthenticatedUser user, UUID workspaceId,
                                  TransferOwnershipRequest request) {
        authorization.requireOwner(workspaceId, user);
        if (request.newOwnerUserId().equals(user.id())) {
            throw new BadRequestException("You already own this workspace");
        }
        Workspace workspace = loadActive(workspaceId);

        WorkspaceMember newOwner = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, request.newOwnerUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member", request.newOwnerUserId()));
        if (newOwner.getStatus() == MemberStatus.SUSPENDED) {
            throw new ConflictException("A suspended member cannot become the owner");
        }
        // The previous owner stays an ADMIN; the new owner must be one too.
        newOwner.assignRole(WorkspaceRole.ADMIN, user.id());
        workspace.transferOwnershipTo(request.newOwnerUserId());

        eventPublisher.publish(WorkspaceEvents.OwnershipTransferred.of(
                workspaceId, user.id(), user.id(), request.newOwnerUserId()));
        accessCache.evict(workspaceId, user.id());
        accessCache.evict(workspaceId, request.newOwnerUserId());
    }

    @Transactional
    public void suspend(AuthenticatedUser user, UUID workspaceId) {
        requireWorkspaceManagePermission(user);
        Workspace workspace = loadActive(workspaceId);
        workspace.suspend();
        workspace.markUpdatedBy(user.id());
        eventPublisher.publish(WorkspaceEvents.WorkspaceSuspended.of(workspaceId, user.id()));
        accessCache.evictWorkspace(workspaceId);
    }

    @Transactional
    public void reactivate(AuthenticatedUser user, UUID workspaceId) {
        requireWorkspaceManagePermission(user);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        workspace.reactivate();
        workspace.markUpdatedBy(user.id());
        eventPublisher.publish(WorkspaceEvents.WorkspaceReactivated.of(workspaceId, user.id()));
        accessCache.evictWorkspace(workspaceId);
    }

    private Workspace loadActive(UUID workspaceId) {
        return workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }


    private void requireWorkspaceManagePermission(AuthenticatedUser user) {
        if (!user.hasPermission(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE)) {
            throw new ForbiddenOperationException(
                    "You don't have permission to manage workspaces");
        }
    }
}
