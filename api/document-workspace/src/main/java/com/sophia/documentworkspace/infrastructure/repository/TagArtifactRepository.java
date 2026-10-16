package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.TagArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TagArtifactRepository extends JpaRepository<TagArtifact, UUID> {

    List<TagArtifact> findAllByArtifactId(UUID artifactId);

    List<TagArtifact> findAllByTagId(UUID tagId);

    boolean existsByTagIdAndArtifactId(UUID tagId, UUID artifactId);

    void deleteByTagIdAndArtifactId(UUID tagId, UUID artifactId);

    void deleteAllByTagId(UUID tagId);

    @Modifying
    @Query("delete from TagArtifact ta where ta.artifactId in :artifactIds")
    void deleteAllByArtifactIdIn(@Param("artifactIds") Collection<UUID> artifactIds);
}
