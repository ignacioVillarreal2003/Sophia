package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.ArtifactMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.AclEntryRepository;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.FavoriteRepository;
import com.sophia.documentworkspace.infrastructure.repository.TagArtifactRepository;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.FileVersion;
import com.sophia.documentworkspace.infrastructure.repository.CommentRepository;
import com.sophia.documentworkspace.infrastructure.repository.FileVersionRepository;
import com.sophia.documentworkspace.infrastructure.repository.DocumentVersionRepository;
import com.sophia.documentworkspace.infrastructure.repository.YjsUpdateRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Soft-delete lifecycle: trash (subtree), restore (with re-parenting to root
 * when the original parent is gone), listing, emptying, and the scheduled
 * retention purge that physically removes expired rows plus their ACLs,
 * favorites and tag assignments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

    private static final int PURGE_BATCH_SIZE = 500;

    private final ArtifactRepository artifactRepository;
    private final AclEntryRepository aclEntryRepository;
    private final FavoriteRepository favoriteRepository;
    private final TagArtifactRepository tagArtifactRepository;
    private final YjsUpdateRepository yjsUpdateRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final FileVersionRepository fileVersionRepository;
    private final CommentRepository commentRepository;
    private final StorageService storageService;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ArtifactAccessService artifactAccess;
    private final DomainEventPublisher eventPublisher;
    private final ArtifactMapper artifactMapper;
    private final ApplicationProperties properties;

    @Transactional
    public void trash(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.DELETE);
        Artifact artifact = result.artifact();

        artifactRepository.softDeleteSubtree(workspaceId, artifactId,
                artifact.subtreePrefix(), Instant.now(), user.id());

        eventPublisher.publish(ArtifactEvents.ArtifactTrashed.of(
                workspaceId, user.id(), artifactId, artifact.getType()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ArtifactResponse> listTrash(AuthenticatedUser user, UUID workspaceId,
                                                    Pageable pageable) {
        var access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.VIEW_CONTENT);
        return PageResponse.from(artifactRepository.findTrashRoots(workspaceId, pageable),
                artifactMapper::toResponse);
    }

    @Transactional
    public ArtifactResponse restore(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.requireIncludingTrashed(user, workspaceId, artifactId,
                AccessMode.DELETE);
        Artifact artifact = result.artifact();
        if (!artifact.isDeleted()) {
            throw new ConflictException("Artifact is not in the trash");
        }

        // If the original parent is gone or still trashed, re-parent to the root.
        if (artifact.getParentId() != null) {
            boolean parentAlive = artifactRepository
                    .findByIdAndWorkspaceIdAndDeletedAtIsNull(artifact.getParentId(), workspaceId)
                    .isPresent();
            if (!parentAlive) {
                String oldPrefix = artifact.subtreePrefix();
                artifact.moveTo(null);
                artifactRepository.rebasePaths(workspaceId, oldPrefix, artifact.subtreePrefix());
            }
        }
        artifactRepository.restoreSubtree(workspaceId, artifactId, artifact.subtreePrefix());
        artifact.restore();

        eventPublisher.publish(ArtifactEvents.ArtifactRestored.of(workspaceId, user.id(), artifactId));
        return artifactMapper.toResponse(artifact);
    }

    /** Permanently deletes one trashed artifact (and subtree) right away. */
    @Transactional
    public void purgeNow(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.requireIncludingTrashed(user, workspaceId, artifactId,
                AccessMode.DELETE);
        Artifact artifact = result.artifact();
        if (!artifact.isDeleted()) {
            throw new ConflictException("Only trashed artifacts can be permanently deleted");
        }

        List<UUID> subtreeIds = new java.util.ArrayList<>();
        subtreeIds.add(artifactId);
        artifactRepository.findSubtreeIncludingTrashed(workspaceId, artifact.subtreePrefix() + "%")
                .forEach(a -> subtreeIds.add(a.getId()));

        hardDelete(subtreeIds);
        eventPublisher.publish(ArtifactEvents.ArtifactPurged.of(workspaceId, user.id(), artifactId));
    }

    /** Empties the caller-visible trash of a workspace. */
    @Transactional
    public int emptyTrash(AuthenticatedUser user, UUID workspaceId) {
        workspaceAuthorization.requirePermission(workspaceId, user, WorkspacePermission.DELETE_CONTENT);
        int purged = 0;
        var roots = artifactRepository.findTrashRoots(workspaceId, Pageable.unpaged()).getContent();
        for (Artifact root : roots) {
            List<UUID> subtreeIds = new java.util.ArrayList<>();
            subtreeIds.add(root.getId());
            artifactRepository.findSubtreeIncludingTrashed(workspaceId, root.subtreePrefix() + "%")
                    .forEach(a -> subtreeIds.add(a.getId()));
            hardDelete(subtreeIds);
            eventPublisher.publish(ArtifactEvents.ArtifactPurged.of(workspaceId, user.id(), root.getId()));
            purged += subtreeIds.size();
        }
        return purged;
    }

    /** Physically removes every artifact of a workspace (and its stored files) ahead of the workspace row itself. */
    @Transactional
    public void purgeWorkspaceContent(UUID workspaceId) {
        hardDelete(artifactRepository.findAllIdsByWorkspaceId(workspaceId));
    }

    /** Nightly retention purge honouring the per-workspace retention override. */
    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "trash-purge", lockAtMostFor = "PT30M")
    @Transactional
    public void purgeExpired() {
        int defaultDays = properties.retention().trashRetentionDays();
        int total = 0;
        while (true) {
            List<UUID> ids = artifactRepository.findPurgeableIds(defaultDays, PURGE_BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            hardDelete(ids);
            total += ids.size();
        }
        if (total > 0) {
            log.info("Trash purge removed {} artifacts past their retention window", total);
        }
    }

    private void hardDelete(List<UUID> artifactIds) {
        if (artifactIds.isEmpty()) {
            return;
        }
        // Storage keys are collected before the rows disappear; the objects are
        // removed afterwards, only when no surviving artifact still shares them
        // (duplicates reference the same immutable object).
        java.util.Set<String> candidateKeys = new java.util.HashSet<>();
        java.util.Set<String> previewKeys = new java.util.HashSet<>();
        for (Artifact artifact : artifactRepository.findAllById(artifactIds)) {
            if (artifact instanceof FileArtifact file) {
                if (file.getStorageKey() != null) {
                    candidateKeys.add(file.getStorageKey());
                }
                if (file.getThumbnailStorageKey() != null) {
                    previewKeys.add(file.getThumbnailStorageKey());
                }
                if (file.getPreviewStorageKey() != null) {
                    previewKeys.add(file.getPreviewStorageKey());
                }
            }
        }
        for (FileVersion version : fileVersionRepository.findAllByArtifactIdIn(artifactIds)) {
            candidateKeys.add(version.getStorageKey());
        }

        aclEntryRepository.deleteAllByResourceIdIn(artifactIds);
        favoriteRepository.deleteAllByArtifactIdIn(artifactIds);
        tagArtifactRepository.deleteAllByArtifactIdIn(artifactIds);
        documentVersionRepository.deleteAllByArtifactIdIn(artifactIds);
        yjsUpdateRepository.deleteAllByDocumentIdIn(artifactIds);
        fileVersionRepository.deleteAllByArtifactIdIn(artifactIds);
        commentRepository.deleteAllByArtifactIdIn(artifactIds);
        artifactRepository.deleteAllByIdIn(artifactIds);

        for (String key : candidateKeys) {
            if (artifactRepository.countByStorageKey(key) == 0
                    && fileVersionRepository.countByStorageKey(key) == 0) {
                storageService.deleteObjectQuietly(key);
            }
        }
        // Preview assets are per-artifact, never shared.
        previewKeys.forEach(storageService::deleteObjectQuietly);
    }
}
