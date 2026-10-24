package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Convenience facade used by every artifact-facing service: resolves the
 * workspace access snapshot, loads the artifact and runs the full policy
 * chain (role + ownership) for the attempted mode.
 */
@Service
@RequiredArgsConstructor
public class ArtifactAccessService {

    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ResourceAccessEvaluator accessEvaluator;
    private final ArtifactRepository artifactRepository;

    public record ArtifactAccess(Artifact artifact, WorkspaceAccessSnapshot access) {
    }

    public ArtifactAccess require(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                  AccessMode mode) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        Artifact artifact = artifactRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(artifactId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Artifact", artifactId));
        accessEvaluator.require(access, artifact, mode, artifact.resourceChain());
        return new ArtifactAccess(artifact, access);
    }

    /** Variant that also finds trashed artifacts (trash listing, restore, purge). */
    public ArtifactAccess requireIncludingTrashed(AuthenticatedUser user, UUID workspaceId,
                                                  UUID artifactId, AccessMode mode) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requireActiveMember(workspaceId, user);
        Artifact artifact = artifactRepository.findByIdAndWorkspaceId(artifactId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Artifact", artifactId));
        accessEvaluator.require(access, artifact, mode, artifact.resourceChain());
        return new ArtifactAccess(artifact, access);
    }
}
