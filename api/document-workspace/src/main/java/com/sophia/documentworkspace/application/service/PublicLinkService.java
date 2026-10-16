package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.dto.request.CreatePublicLinkRequest;
import com.sophia.documentworkspace.domain.dto.response.PublicLinkResponse;
import com.sophia.documentworkspace.domain.dto.response.PublicViewResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.PublicLink;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.PublicLinkRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only public links. Creating one requires the workspace to allow public sharing, the SHARE
 * permission on the artifact; viewing needs only the token.
 */
@Service
@RequiredArgsConstructor
public class PublicLinkService {

    private static final Set<ArtifactType.Kind> SHAREABLE_KINDS = Set.of(
            ArtifactType.Kind.DOCUMENT);

    private final ArtifactAccessService artifactAccess;
    private final ArtifactRepository artifactRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PublicLinkRepository linkRepository;
    private final ExportService exportService;
    private final com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService authorization;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public PublicLinkResponse create(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                     CreatePublicLinkRequest request) {
        var access = artifactAccess.require(user, workspaceId, artifactId, AccessMode.SHARE);
        Artifact artifact = access.artifact();
        requireEnabled(workspaceId);
        if (!SHAREABLE_KINDS.contains(artifact.kind())) {
            throw new BadRequestException("Only documents, tables and code snippets can be shared with a public link");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = request != null && request.expiresInDays() != null
                ? Instant.now().plus(Duration.ofDays(request.expiresInDays()))
                : null;
        PublicLink link = linkRepository.save(
                PublicLink.create(workspaceId, artifactId, hash(token), user.id(), expiresAt));
        return toResponse(link, token);
    }

    @Transactional(readOnly = true)
    public List<PublicLinkResponse> list(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.SHARE);
        Instant now = Instant.now();
        return linkRepository.findAllByArtifactIdAndRevokedAtIsNullOrderByCreatedAtDesc(artifactId).stream()
                .filter(link -> link.isActive(now))
                .map(link -> toResponse(link, null))
                .toList();
    }

    @Transactional
    public void revoke(AuthenticatedUser user, UUID workspaceId, UUID artifactId, UUID linkId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.SHARE);
        PublicLink link = linkRepository.findByIdAndArtifactId(linkId, artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("Public link", linkId));
        link.revoke();
    }

    /** How many public links of the workspace currently work. */
    @Transactional(readOnly = true)
    public long countActive(AuthenticatedUser user, UUID workspaceId) {
        authorization.requirePermission(workspaceId, user,
                com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission.MANAGE_WORKSPACE);
        return linkRepository.countActive(workspaceId, Instant.now());
    }

    /** "Stop sharing everything": revokes every public link of the workspace; returns how many were active. */
    @Transactional
    public long revokeAll(AuthenticatedUser user, UUID workspaceId) {
        authorization.requirePermission(workspaceId, user,
                com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission.MANAGE_WORKSPACE);
        long active = linkRepository.countActive(workspaceId, Instant.now());
        linkRepository.revokeAll(workspaceId, Instant.now());
        return active;
    }

    /** Anonymous access: unknown, revoked, expired or no-longer-allowed links are all just "not found". */
    @Transactional(readOnly = true)
    public PublicViewResponse view(String token) {
        PublicLink link = linkRepository.findByTokenHash(hash(token))
                .filter(l -> l.isActive(Instant.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Public link", token.length() > 8 ? token.substring(0, 8) : token));
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(link.getWorkspaceId())
                .filter(w -> w.isActive() && publicSharingOn(w))
                .orElseThrow(() -> new ResourceNotFoundException("Public link", link.getId()));
        Artifact artifact = artifactRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(link.getArtifactId(), link.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Public link", link.getId()));
        return new PublicViewResponse(artifact.getName(), artifact.getType().name(), workspace.getName(),
                artifact.getUpdatedAt(), exportService.markdownFor(artifact));
    }

    private void requireEnabled(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!publicSharingOn(workspace)) {
            throw new ConflictException("Public links are disabled for this workspace");
        }
    }

    private boolean publicSharingOn(Workspace workspace) {
        return workspace.isPublicSharingEnabled();
    }

    private PublicLinkResponse toResponse(PublicLink link, String token) {
        return new PublicLinkResponse(link.getId(), link.getArtifactId(), link.getCreatedBy(),
                link.getCreatedAt(), link.getExpiresAt(), token);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
