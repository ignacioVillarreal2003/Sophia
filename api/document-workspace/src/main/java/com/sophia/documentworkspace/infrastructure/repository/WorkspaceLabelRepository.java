package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.WorkspaceLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceLabelRepository extends JpaRepository<WorkspaceLabel, UUID> {

    List<WorkspaceLabel> findAllByUserIdOrderByNameAsc(UUID userId);

    Optional<WorkspaceLabel> findByIdAndUserId(UUID id, UUID userId);

    List<WorkspaceLabel> findAllByIdInAndUserId(Collection<UUID> ids, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    long countByUserId(UUID userId);
}
