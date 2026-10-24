package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.WorkspaceInvitation;
import com.sophia.documentworkspace.domain.entity.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<WorkspaceInvitation> findByIdAndInviteeUserId(UUID id, UUID inviteeUserId);

    Optional<WorkspaceInvitation> findFirstByWorkspaceIdAndInviteeUserIdAndStatus(
            UUID workspaceId, UUID inviteeUserId, InvitationStatus status);

    List<WorkspaceInvitation> findAllByWorkspaceIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID workspaceId, InvitationStatus status, Instant now);

}
