package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    List<FileVersion> findAllByArtifactIdOrderBySeqDesc(UUID artifactId);

    Optional<FileVersion> findByIdAndArtifactId(UUID id, UUID artifactId);

    Optional<FileVersion> findFirstByArtifactIdOrderBySeqDesc(UUID artifactId);

    List<FileVersion> findAllByArtifactIdIn(Collection<UUID> artifactIds);

    long countByStorageKey(String storageKey);

    void deleteAllByArtifactIdIn(Collection<UUID> artifactIds);
}
