package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.AccessRequest;
import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    Optional<AccessRequest> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<AccessRequest> findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, RequestStatus status);

    Optional<AccessRequest> findFirstByWorkspaceIdAndUserIdAndStatus(UUID workspaceId, UUID userId, RequestStatus status);

    Optional<AccessRequest> findFirstByWorkspaceIdAndUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, RequestStatus status);
}
