package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.Workspace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID>, JpaSpecificationExecutor<Workspace> {

    Optional<Workspace> findByIdAndDeletedAtIsNull(UUID id);

    Page<Workspace> findAllByOwnerIdAndDeletedAtIsNotNull(UUID ownerId, Pageable pageable);

    /** Bumps the activity timestamp at most once per window, so busy workspaces do not hammer one row. */
    @Modifying
    @Query("update Workspace w set w.lastActivityAt = :now where w.id = :id and w.lastActivityAt < :threshold")
    int touchActivity(@Param("id") UUID id, @Param("now") Instant now, @Param("threshold") Instant threshold);

    /** Trashed workspaces whose retention elapsed: ready to be erased for good. */
    List<Workspace> findAllByDeletedAtIsNotNullAndDeletedAtBefore(Instant cutoff, Pageable pageable);

    /** Trashed workspaces inside the warning window that have not been warned yet. */
    @Query("""
            select w from Workspace w
            where w.deletedAt is not null and w.deletedAt < :warnCutoff and w.deletedAt >= :purgeCutoff
              and w.purgeWarningSentAt is null
            """)
    List<Workspace> findToWarn(@Param("warnCutoff") Instant warnCutoff, @Param("purgeCutoff") Instant purgeCutoff);

}
