package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.TagMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.CreateTagRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateTagRequest;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.TagResponse;
import com.sophia.documentworkspace.domain.entity.Tag;
import com.sophia.documentworkspace.domain.entity.TagArtifact;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.repository.TagArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TagArtifactRepository tagArtifactRepository;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ArtifactAccessService artifactAccess;
    private final TagMapper tagMapper;

    @Transactional
    public TagResponse create(AuthenticatedUser user, UUID workspaceId, CreateTagRequest request) {
        workspaceAuthorization.requirePermission(workspaceId, user, WorkspacePermission.CREATE_CONTENT);
        if (tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, request.name())) {
            throw new ConflictException(ErrorCode.DUPLICATE_RESOURCE,
                    "A tag named '%s' already exists in this workspace".formatted(request.name()));
        }
        UUID parentTagId = validateParent(workspaceId, request.parentTagId(), null);
        Tag tag = tagRepository.save(
                Tag.create(workspaceId, request.name(), request.color(), parentTagId));
        return tagMapper.toResponse(tag);
    }

    @Transactional
    public TagResponse update(AuthenticatedUser user, UUID workspaceId, UUID tagId,
                              UpdateTagRequest request) {
        workspaceAuthorization.requirePermission(workspaceId, user, WorkspacePermission.EDIT_CONTENT);
        Tag tag = load(workspaceId, tagId);
        if (request.name() != null
                && !request.name().equalsIgnoreCase(tag.getName())
                && tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, request.name())) {
            throw new ConflictException(ErrorCode.DUPLICATE_RESOURCE,
                    "A tag named '%s' already exists in this workspace".formatted(request.name()));
        }
        UUID parentTagId = validateParent(workspaceId, request.parentTagId(), tagId);
        tag.update(request.name(), request.color(), parentTagId);
        return tagMapper.toResponse(tag);
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID workspaceId, UUID tagId) {
        workspaceAuthorization.requirePermission(workspaceId, user, WorkspacePermission.DELETE_CONTENT);
        Tag tag = load(workspaceId, tagId);
        if (tagRepository.existsByParentTagId(tagId)) {
            throw new ConflictException("Delete or re-parent the child tags first");
        }
        tagArtifactRepository.deleteAllByTagId(tagId);
        tagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public PageResponse<TagResponse> list(AuthenticatedUser user, UUID workspaceId, Pageable pageable) {
        workspaceAuthorization.requireActiveMember(workspaceId, user);
        return PageResponse.from(tagRepository.findAllByWorkspaceId(workspaceId, pageable),
                tagMapper::toResponse);
    }

    @Transactional
    public void assign(AuthenticatedUser user, UUID workspaceId, UUID tagId, UUID artifactId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.WRITE);
        load(workspaceId, tagId);
        if (!tagArtifactRepository.existsByTagIdAndArtifactId(tagId, artifactId)) {
            tagArtifactRepository.save(TagArtifact.of(tagId, artifactId, user.id()));
        }
    }

    @Transactional
    public void unassign(AuthenticatedUser user, UUID workspaceId, UUID tagId, UUID artifactId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.WRITE);
        load(workspaceId, tagId);
        tagArtifactRepository.deleteByTagIdAndArtifactId(tagId, artifactId);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> tagsOfArtifact(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        List<UUID> tagIds = tagArtifactRepository.findAllByArtifactId(artifactId).stream()
                .map(TagArtifact::getTagId)
                .toList();
        return tagRepository.findAllById(tagIds).stream()
                .map(tagMapper::toResponse)
                .toList();
    }

    private Tag load(UUID workspaceId, UUID tagId) {
        return tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
    }

    private UUID validateParent(UUID workspaceId, UUID parentTagId, UUID selfId) {
        if (parentTagId == null) {
            return null;
        }
        if (parentTagId.equals(selfId)) {
            throw new BadRequestException(ErrorCode.INVALID_HIERARCHY, "A tag cannot be its own parent");
        }
        load(workspaceId, parentTagId);
        return parentTagId;
    }
}
