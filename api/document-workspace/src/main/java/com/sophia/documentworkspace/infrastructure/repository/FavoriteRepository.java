package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Optional<Favorite> findByUserIdAndArtifactId(UUID userId, UUID artifactId);

    Page<Favorite> findAllByUserIdAndWorkspaceId(UUID userId, UUID workspaceId, Pageable pageable);

    Page<Favorite> findAllByUserIdAndWorkspaceIdAndPinnedTrue(UUID userId, UUID workspaceId,
                                                              Pageable pageable);

    @Modifying
    @Query("delete from Favorite f where f.artifactId in :artifactIds")
    void deleteAllByArtifactIdIn(@Param("artifactIds") Collection<UUID> artifactIds);
}
