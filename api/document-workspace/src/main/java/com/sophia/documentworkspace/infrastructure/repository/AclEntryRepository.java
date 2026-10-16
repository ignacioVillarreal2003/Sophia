package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.AclEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AclEntryRepository extends JpaRepository<AclEntry, UUID> {

    List<AclEntry> findAllByResourceId(UUID resourceId);

    List<AclEntry> findAllByResourceIdIn(Collection<UUID> resourceIds);

    Optional<AclEntry> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<AclEntry> findByResourceIdAndSubjectUserId(UUID resourceId, UUID subjectUserId);

    @Modifying
    @Query("delete from AclEntry e where e.resourceId in :resourceIds")
    void deleteAllByResourceIdIn(@Param("resourceIds") Collection<UUID> resourceIds);

    @Modifying
    @Query("delete from AclEntry e where e.workspaceId = :workspaceId and e.subjectUserId = :userId")
    void deleteAllForUserInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
