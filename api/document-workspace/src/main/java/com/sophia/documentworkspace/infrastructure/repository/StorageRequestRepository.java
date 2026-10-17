package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.StorageRequest;
import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageRequestRepository extends JpaRepository<StorageRequest, UUID> {

    Optional<StorageRequest> findFirstByWorkspaceIdAndStatus(UUID workspaceId, RequestStatus status);

    Optional<StorageRequest> findFirstByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<StorageRequest> findAllByWorkspaceIdInAndStatus(Collection<UUID> workspaceIds, RequestStatus status);

    Page<StorageRequest> findAllByStatusOrderByCreatedAtAsc(RequestStatus status, Pageable pageable);

    long countByStatus(RequestStatus status);
}
