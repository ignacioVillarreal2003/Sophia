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

    /**
     * Trashed workspaces whose retention elapsed, oldest first. The retention is the owner's own choice when there is
     * one ({@code user_workspace_preferences}) and {@code defaultDays} otherwise.
     */
    @Query(value = """
            select w.* from workspaces w
            left join user_workspace_preferences p on p.user_id = w.owner_id
            where w.deleted_at is not null
              and w.deleted_at < now() - make_interval(days => coalesce(p.trash_retention_days, :defaultDays))
            order by w.deleted_at
            limit :limit
            """, nativeQuery = true)
    List<Workspace> findExpired(@Param("defaultDays") int defaultDays, @Param("limit") int limit);

    /**
     * Trashed workspaces inside the last {@code warnDays} of their retention that have not been warned yet. A
     * retention no longer than the warning gets no warning: it would arrive the moment the workspace was deleted.
     */
    @Query(value = """
            select w.* from workspaces w
            left join user_workspace_preferences p on p.user_id = w.owner_id
            where w.deleted_at is not null and w.purge_warning_sent_at is null
              and coalesce(p.trash_retention_days, :defaultDays) > :warnDays
              and w.deleted_at < now() - make_interval(days => coalesce(p.trash_retention_days, :defaultDays) - :warnDays)
              and w.deleted_at >= now() - make_interval(days => coalesce(p.trash_retention_days, :defaultDays))
            """, nativeQuery = true)
    List<Workspace> findToWarn(@Param("defaultDays") int defaultDays, @Param("warnDays") int warnDays);
}
