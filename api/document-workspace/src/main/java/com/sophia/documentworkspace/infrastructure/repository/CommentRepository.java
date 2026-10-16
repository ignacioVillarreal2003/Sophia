package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Optional<Comment> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("""
            select c from Comment c
            where c.artifactId = :artifactId
              and c.parentCommentId is null
              and (:blockId is null or c.blockId = :blockId)
              and (:includeResolved = true or c.resolvedAt is null)
            order by c.createdAt asc
            """)
    Page<Comment> findThreads(@Param("artifactId") UUID artifactId,
                              @Param("blockId") UUID blockId,
                              @Param("includeResolved") boolean includeResolved,
                              Pageable pageable);

    List<Comment> findAllByParentCommentIdInOrderByCreatedAtAsc(Collection<UUID> parentIds);

    List<Comment> findAllByArtifactIdAndDeletedAtIsNull(UUID artifactId);

    boolean existsByIdAndArtifactId(UUID id, UUID artifactId);

    @Modifying
    @Query("delete from Comment c where c.artifactId in :artifactIds")
    void deleteAllByArtifactIdIn(@Param("artifactIds") Collection<UUID> artifactIds);
}
