package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.PublicLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicLinkRepository extends JpaRepository<PublicLink, UUID> {

    Optional<PublicLink> findByTokenHash(String tokenHash);

    List<PublicLink> findAllByArtifactIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID artifactId);

    Optional<PublicLink> findByIdAndArtifactId(UUID id, UUID artifactId);

    @Query("""
            select count(l) from PublicLink l
            where l.workspaceId = :workspaceId and l.revokedAt is null
              and (l.expiresAt is null or l.expiresAt > :now)
            """)
    long countActive(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);

    @Modifying
    @Query("update PublicLink l set l.revokedAt = :now where l.workspaceId = :workspaceId and l.revokedAt is null")
    int revokeAll(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);
}
