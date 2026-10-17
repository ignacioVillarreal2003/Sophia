package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<WorkspaceMember> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);

    long countByWorkspaceId(UUID workspaceId);

    List<WorkspaceMember> findAllByUserIdAndWorkspaceIdIn(UUID userId, Collection<UUID> workspaceIds);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);

    /** Admin role holders (the owner included) who should hear about requests for the workspace. */
    List<WorkspaceMember> findAllByWorkspaceIdAndRoleAndStatus(UUID workspaceId,
            com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole role,
            com.sophia.documentworkspace.domain.entity.enums.MemberStatus status);

    void deleteAllByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
