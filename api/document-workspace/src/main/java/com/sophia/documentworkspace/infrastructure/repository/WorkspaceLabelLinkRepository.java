package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.WorkspaceLabelLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkspaceLabelLinkRepository extends JpaRepository<WorkspaceLabelLink, UUID> {

    /** Links of the given workspaces that carry one of the user's own labels. */
    @Query("""
            select l from WorkspaceLabelLink l, WorkspaceLabel b
            where l.labelId = b.id and b.userId = :userId and l.workspaceId in :workspaceIds
            """)
    List<WorkspaceLabelLink> findForUser(@Param("userId") UUID userId,
                                         @Param("workspaceIds") Collection<UUID> workspaceIds);

    /** Removes every label the user put on one workspace (before the new set is stored). */
    @Modifying
    @Query("""
            delete from WorkspaceLabelLink l
            where l.workspaceId = :workspaceId
              and l.labelId in (select b.id from WorkspaceLabel b where b.userId = :userId)
            """)
    void deleteForUserAndWorkspace(@Param("userId") UUID userId, @Param("workspaceId") UUID workspaceId);

    /** Drops every label the user put on a workspace they just left. */
    default void clear(UUID userId, UUID workspaceId) {
        deleteForUserAndWorkspace(userId, workspaceId);
    }
}
