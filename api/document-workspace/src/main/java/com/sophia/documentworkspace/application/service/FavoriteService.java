package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.ArtifactMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.FavoriteResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.Favorite;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactAccessService artifactAccess;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final RecentArtifactsService recentArtifacts;
    private final ArtifactMapper artifactMapper;

    @Transactional
    public FavoriteResponse addFavorite(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        Favorite favorite = favoriteRepository.findByUserIdAndArtifactId(user.id(), artifactId)
                .orElseGet(() -> favoriteRepository.save(
                        Favorite.of(workspaceId, user.id(), artifactId)));
        return toResponse(favorite, artifactMapper.toResponse(result.artifact()));
    }

    @Transactional
    public void removeFavorite(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        workspaceAuthorization.requireActiveMember(workspaceId, user);
        Favorite favorite = favoriteRepository.findByUserIdAndArtifactId(user.id(), artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite", artifactId));
        favoriteRepository.delete(favorite);
    }

    @Transactional
    public FavoriteResponse setPinned(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                      boolean pinned) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        Favorite favorite = favoriteRepository.findByUserIdAndArtifactId(user.id(), artifactId)
                .orElseGet(() -> favoriteRepository.save(
                        Favorite.of(workspaceId, user.id(), artifactId)));
        if (pinned) {
            favorite.pin();
        } else {
            favorite.unpin();
        }
        return toResponse(favorite, artifactMapper.toResponse(result.artifact()));
    }

    @Transactional(readOnly = true)
    public PageResponse<FavoriteResponse> list(AuthenticatedUser user, UUID workspaceId,
                                               boolean pinnedOnly, Pageable pageable) {
        var access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        Page<Favorite> page = pinnedOnly
                ? favoriteRepository.findAllByUserIdAndWorkspaceIdAndPinnedTrue(user.id(), workspaceId, pageable)
                : favoriteRepository.findAllByUserIdAndWorkspaceId(user.id(), workspaceId, pageable);

        Map<UUID, Artifact> artifacts = artifactRepository
                .findAllByIdInAndWorkspaceId(
                        page.getContent().stream().map(Favorite::getArtifactId).toList(), workspaceId)
                .stream()
                .filter(a -> !a.isDeleted())
                .collect(Collectors.toMap(Artifact::getId, a -> a));

        List<FavoriteResponse> content = page.getContent().stream()
                .filter(f -> artifacts.containsKey(f.getArtifactId()))
                .map(f -> toResponse(f, artifactMapper.toResponse(artifacts.get(f.getArtifactId()))))
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    /** Recently opened artifacts, newest first, filtered by visibility. */
    @Transactional(readOnly = true)
    public List<ArtifactResponse> listRecent(AuthenticatedUser user, UUID workspaceId, int limit) {
        var access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        List<UUID> recentIds = recentArtifacts.recentArtifactIds(user.id(), workspaceId, limit);
        if (recentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Artifact> byId = artifactRepository
                .findAllByIdInAndWorkspaceId(recentIds, workspaceId).stream()
                .filter(a -> !a.isDeleted())
                .collect(Collectors.toMap(Artifact::getId, a -> a));
        return recentIds.stream()
                .map(byId::get)
                .filter(a -> a != null)
                .map(artifactMapper::toResponse)
                .toList();
    }

    private FavoriteResponse toResponse(Favorite favorite, ArtifactResponse artifact) {
        return new FavoriteResponse(favorite.getId(), favorite.getWorkspaceId(),
                favorite.getArtifactId(), favorite.isPinned(), favorite.getCreatedAt(), artifact);
    }
}
