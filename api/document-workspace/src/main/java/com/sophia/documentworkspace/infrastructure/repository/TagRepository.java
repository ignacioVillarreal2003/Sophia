package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<Tag> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<Tag> findAllByWorkspaceIdAndParentTagId(UUID workspaceId, UUID parentTagId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByParentTagId(UUID parentTagId);
}
