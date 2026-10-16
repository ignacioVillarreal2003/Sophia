package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.DocumentVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    Optional<DocumentVersion> findFirstByArtifactIdOrderByVersionDesc(UUID artifactId);

    Optional<DocumentVersion> findByIdAndArtifactId(UUID id, UUID artifactId);

    Page<DocumentVersion> findAllByArtifactIdOrderByVersionDesc(UUID artifactId, Pageable pageable);

    Page<DocumentVersion> findAllByArtifactIdAndNameIsNotNullOrderByVersionDesc(UUID artifactId, Pageable pageable);

    long countByArtifactId(UUID artifactId);

    /** Oldest unnamed snapshots first, used to trim history. */
    @Query("select v from DocumentVersion v where v.artifactId = :artifactId and v.name is null order by v.version asc")
    List<DocumentVersion> findUnnamedOldestFirst(UUID artifactId, Pageable pageable);

    @Modifying
    @Query("delete from DocumentVersion v where v.artifactId in :artifactIds")
    void deleteAllByArtifactIdIn(Collection<UUID> artifactIds);
}
