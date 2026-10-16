package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.AclEntryMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.dto.request.GrantAclRequest;
import com.sophia.documentworkspace.domain.dto.response.AclEntryResponse;
import com.sophia.documentworkspace.domain.entity.AclEntry;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.event.PermissionEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.AclEntryRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-resource ACL management. Grants require MANAGE_PERMISSIONS on the target
 * artifact (workspace admins, or a member granted it on that item). One entry exists per
 * (resource, subject); repeated grants merge modes into the existing entry.
 */
@Service
@RequiredArgsConstructor
public class ResourcePermissionService {

    private final AclEntryRepository aclEntryRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ArtifactAccessService artifactAccess;
    private final DomainEventPublisher eventPublisher;
    private final AclEntryMapper aclEntryMapper;

    @Transactional
    public AclEntryResponse grant(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                  GrantAclRequest request) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.MANAGE_PERMISSIONS);
        validateSubject(workspaceId, request);

        AclEntry entry = aclEntryRepository
                .findByResourceIdAndSubjectUserId(artifactId, request.subjectUserId())
                .map(existing -> merge(existing, request.modes()))
                .orElseGet(() -> aclEntryRepository.save(AclEntry.forUser(
                        workspaceId, artifactId, request.subjectUserId(),
                        request.modes(), user.id(), request.expiresAt())));

        eventPublisher.publish(PermissionEvents.AclGranted.of(
                workspaceId, user.id(), entry.getId(), artifactId,
                entry.getSubjectUserId(), entry.getModes()));
        return aclEntryMapper.toResponse(entry);
    }

    @Transactional
    public void revoke(AuthenticatedUser user, UUID workspaceId, UUID artifactId, UUID aclEntryId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.MANAGE_PERMISSIONS);
        AclEntry entry = aclEntryRepository.findByIdAndWorkspaceId(aclEntryId, workspaceId)
                .filter(e -> e.getResourceId().equals(artifactId))
                .orElseThrow(() -> new ResourceNotFoundException("ACL entry", aclEntryId));

        aclEntryRepository.delete(entry);
        eventPublisher.publish(PermissionEvents.AclRevoked.of(
                workspaceId, user.id(), aclEntryId, artifactId));
    }

    @Transactional(readOnly = true)
    public List<AclEntryResponse> list(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.MANAGE_PERMISSIONS);
        return aclEntryRepository.findAllByResourceId(artifactId).stream()
                .map(aclEntryMapper::toResponse)
                .toList();
    }

    private AclEntry merge(AclEntry existing, Set<AccessMode> newModes) {
        Set<AccessMode> merged = EnumSet.copyOf(existing.getModes());
        merged.addAll(newModes);
        existing.updateModes(merged);
        return existing;
    }

    private void validateSubject(UUID workspaceId, GrantAclRequest request) {
        if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.subjectUserId())) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "The subject user is not a member of this workspace");
        }
    }
}
