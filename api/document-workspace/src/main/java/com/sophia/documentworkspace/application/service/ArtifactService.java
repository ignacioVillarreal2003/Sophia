package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.ArtifactMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.ResourceAccessEvaluator;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.CreateArtifactRequest;
import com.sophia.documentworkspace.domain.dto.request.DuplicateArtifactRequest;
import com.sophia.documentworkspace.domain.dto.request.MoveArtifactRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateArtifactRequest;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.BreadcrumbEntry;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ArtifactAccessService artifactAccess;
    private final ResourceAccessEvaluator accessEvaluator;
    private final DomainEventPublisher eventPublisher;
    private final ArtifactMapper artifactMapper;
    private final RecentArtifactsService recentArtifacts;

    @Transactional
    public ArtifactResponse create(AuthenticatedUser user, UUID workspaceId,
                                   CreateArtifactRequest request) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        Folder parent = resolveParentForWrite(user, workspaceId, request.parentId());

        if (parent != null) {
            accessEvaluator.require(access, parent, AccessMode.WRITE, parent.resourceChain());
        } else {
            workspaceAuthorization.requirePermission(workspaceId, user,
                    com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission.CREATE_CONTENT);
        }

        Artifact artifact = instantiate(request, workspaceId, parent, user.id());
        artifact = artifactRepository.save(artifact);

        eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(
                workspaceId, user.id(), artifact.getId(), artifact.getType(),
                artifact.getName(), artifact.getParentId()));
        return artifactMapper.toResponse(artifact);
    }

    @Transactional(readOnly = true)
    public ArtifactResponse getById(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        recentArtifacts.recordAccess(user.id(), workspaceId, artifactId);
        return artifactMapper.toResponse(result.artifact());
    }

    @Transactional(readOnly = true)
    public PageResponse<ArtifactResponse> listChildren(AuthenticatedUser user, UUID workspaceId,
                                                       UUID parentId, Pageable pageable) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        Artifact parent = null;
        if (parentId != null) {
            parent = artifactRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(parentId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Artifact", parentId));
            accessEvaluator.require(access, parent, AccessMode.READ, parent.resourceChain());
            if (!(parent instanceof Folder)) {
                throw new BadRequestException(ErrorCode.INVALID_HIERARCHY, "The parent artifact is not a folder");
            }
        }
        String childPath = parentId == null ? "" : ((Folder) parent).childPath();
        return PageResponse.from(artifactRepository.findChildren(workspaceId, childPath, pageable),
                artifactMapper::toResponse);
    }

    /** Documents of the workspace the caller can read, for note links and pickers. */
    @Transactional(readOnly = true)
    public List<ArtifactResponse> findDocuments(AuthenticatedUser user, UUID workspaceId, String text, int limit,
                                                boolean studySources) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        String query = text == null ? "" : text.trim();
        var page = org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        var found = studySources
                ? artifactRepository.findStudySourcesByName(workspaceId, query, page)
                : artifactRepository.findDocumentsByName(workspaceId, query, page);
        return found
                .stream()
                .map(artifactMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BreadcrumbEntry> breadcrumb(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        List<UUID> ancestorIds = result.artifact().ancestorIds();
        if (ancestorIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Artifact> byId = artifactRepository.findAllByIdInAndWorkspaceId(ancestorIds, workspaceId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Artifact::getId, a -> a));
        List<BreadcrumbEntry> breadcrumb = new ArrayList<>();
        for (UUID id : ancestorIds) {
            Artifact ancestor = byId.get(id);
            if (ancestor != null) {
                breadcrumb.add(artifactMapper.toBreadcrumbEntry(ancestor));
            }
        }
        return breadcrumb;
    }

    @Transactional
    public ArtifactResponse update(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                   UpdateArtifactRequest request) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.WRITE);
        Artifact artifact = result.artifact();

        ArtifactEvents.ArtifactUpdated.Snapshot before = new ArtifactEvents.ArtifactUpdated.Snapshot(
                artifact.getName(), artifact.getDescription());

        if (request.name() != null) {
            artifact.rename(request.name());
        }
        if (request.description() != null) {
            artifact.describe(request.description());
        }
        if (request.color() != null && artifact instanceof Folder folder) {
            folder.changeColor(request.color());
        }

        eventPublisher.publish(ArtifactEvents.ArtifactUpdated.of(
                workspaceId, user.id(), artifactId, artifact.getType(), before));
        return artifactMapper.toResponse(artifact);
    }

    @Transactional
    public ArtifactResponse move(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                 MoveArtifactRequest request) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.WRITE);
        Artifact artifact = result.artifact();
        UUID previousParentId = artifact.getParentId();

        Folder newParent = resolveParentForWrite(user, workspaceId, request.newParentId());
        if (newParent != null) {
            if (newParent.getId().equals(artifact.getId()) || newParent.isDescendantOf(artifact)) {
                throw new BadRequestException(ErrorCode.INVALID_HIERARCHY,
                        "Cannot move an artifact into itself or its own subtree");
            }
        }

        String oldPrefix = artifact.subtreePrefix();
        artifact.moveTo(newParent);
        String newPrefix = artifact.subtreePrefix();
        if (!oldPrefix.equals(newPrefix)) {
            artifactRepository.rebasePaths(workspaceId, oldPrefix, newPrefix);
        }

        eventPublisher.publish(ArtifactEvents.ArtifactMoved.of(
                workspaceId, user.id(), artifactId, previousParentId, artifact.getParentId()));
        return artifactMapper.toResponse(artifact);
    }

    @Transactional
    public ArtifactResponse duplicate(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                      DuplicateArtifactRequest request) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        Artifact source = result.artifact();

        Folder targetParent = request.targetParentId() != null
                ? resolveParentForWrite(user, workspaceId, request.targetParentId())
                : resolveParentForWrite(user, workspaceId, source.getParentId());
        if (targetParent != null) {
            accessEvaluator.require(result.access(), targetParent, AccessMode.WRITE,
                    targetParent.resourceChain());
        }

        String newName = request.newName() != null ? request.newName() : source.getName() + " (copy)";
        Artifact copyRoot = artifactRepository.save(source.copyWith(newName, targetParent, user.id()));

        if (source instanceof Folder sourceFolder) {
            Deque<Folder[]> queue = new ArrayDeque<>();
            queue.add(new Folder[]{sourceFolder, (Folder) copyRoot});
            while (!queue.isEmpty()) {
                Folder[] pair = queue.poll();
                var children = artifactRepository.findChildren(
                        workspaceId, pair[0].childPath(), Pageable.unpaged()).getContent();
                for (Artifact child : children) {
                    Artifact childCopy = artifactRepository.save(
                            child.copyWith(child.getName(), pair[1], user.id()));
                    if (child instanceof Folder childFolder && childCopy instanceof Folder copyFolder) {
                        queue.add(new Folder[]{childFolder, copyFolder});
                    }
                }
            }
        }

        eventPublisher.publish(ArtifactEvents.ArtifactDuplicated.of(
                workspaceId, user.id(), artifactId, copyRoot.getId()));
        return artifactMapper.toResponse(copyRoot);
    }

    private Artifact instantiate(CreateArtifactRequest request, UUID workspaceId, Folder parent, UUID ownerId) {
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().strip();
        return switch (request.type().kind()) {
            case FOLDER -> Folder.create(workspaceId, parent, request.name(), ownerId, description, request.color());
            case DOCUMENT -> DocumentArtifact.create(request.type(), workspaceId, parent, request.name(),
                    ownerId, description, initialContent(request));
            case FILE -> throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                    "File artifacts are created through the upload endpoints");
            case TEMPLATE -> throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                    "Templates are created through their dedicated endpoints");
        };
    }

    private Folder resolveParentForWrite(AuthenticatedUser user, UUID workspaceId, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        Artifact parent = artifactRepository
                .findByIdAndWorkspaceIdAndDeletedAtIsNull(parentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent folder", parentId));
        if (!(parent instanceof Folder folder)) {
            throw new BadRequestException(ErrorCode.INVALID_HIERARCHY,
                    "The parent artifact is not a folder");
        }
        return folder;
    }

    private static String initialContent(CreateArtifactRequest request) {
        if (request.content() != null) {
            return request.content();
        }
        return switch (request.type()) {
            case SPREADSHEET -> com.sophia.documentworkspace.domain.util.SpreadsheetCodec.emptyWorkbook();
            case CHECKLIST -> "- [ ] ";
            case PRESENTATION -> com.sophia.documentworkspace.domain.util.PresentationCodec.empty();
            case WHITEBOARD -> com.sophia.documentworkspace.domain.util.WhiteboardCodec.empty();
            default -> "";
        };
    }
}
