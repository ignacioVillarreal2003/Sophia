package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.Artifact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    Optional<Artifact> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    Optional<Artifact> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /**
     * Direct children of a folder: the items whose path is exactly the folder's child path
     * ({@code Folder.childPath()}), or the empty path for the workspace root.
     */
    @Query("""
            select a from Artifact a
            where a.workspaceId = :workspaceId
              and a.path = :childPath
              and a.deletedAt is null
            """)
    Page<Artifact> findChildren(@Param("workspaceId") UUID workspaceId,
                                @Param("childPath") String childPath,
                                Pageable pageable);

    List<Artifact> findAllByIdInAndWorkspaceId(Collection<UUID> ids, UUID workspaceId);

    /** Live documents (notes, checklists, spreadsheets) whose name contains the text, most recently edited first. */
    @Query("""
            select a from Artifact a
            where a.workspaceId = :workspaceId
              and a.deletedAt is null
              and a.type in (com.sophia.documentworkspace.domain.entity.enums.ArtifactType.MARKDOWN_DOCUMENT,
                             com.sophia.documentworkspace.domain.entity.enums.ArtifactType.CHECKLIST,
                             com.sophia.documentworkspace.domain.entity.enums.ArtifactType.SPREADSHEET)
              and (:text = '' or lower(a.name) like lower(concat('%', :text, '%')))
            order by a.updatedAt desc
            """)
    List<Artifact> findDocumentsByName(@Param("workspaceId") UUID workspaceId,
                                       @Param("text") String text,
                                       Pageable pageable);

    /** Whole live subtree of a node, the node excluded. */
    @Query("""
            select a from Artifact a
            where a.workspaceId = :workspaceId
              and a.path like :prefix
              and a.deletedAt is null
            """)
    List<Artifact> findSubtree(@Param("workspaceId") UUID workspaceId,
                               @Param("prefix") String prefixLikePattern);

    /** Whole subtree of a node including trashed rows, the node excluded. */
    @Query("""
            select a from Artifact a
            where a.workspaceId = :workspaceId
              and a.path like :prefix
            """)
    List<Artifact> findSubtreeIncludingTrashed(@Param("workspaceId") UUID workspaceId,
                                               @Param("prefix") String prefixLikePattern);

    /** Re-prefixes the materialized paths of a moved subtree in one statement. */
    @Modifying
    @Query(value = """
            UPDATE artifacts
            SET path = :newPrefix || substr(path, length(:oldPrefix) + 1)
            WHERE workspace_id = :workspaceId
              AND path LIKE :oldPrefix || '%'
            """, nativeQuery = true)
    int rebasePaths(@Param("workspaceId") UUID workspaceId,
                    @Param("oldPrefix") String oldPrefix,
                    @Param("newPrefix") String newPrefix);

    /** Soft-deletes a node and its whole subtree in one statement. */
    @Modifying
    @Query(value = """
            UPDATE artifacts
            SET deleted_at = :deletedAt, deleted_by = :deletedBy
            WHERE workspace_id = :workspaceId
              AND deleted_at IS NULL
              AND (id = :artifactId OR path LIKE :prefix || '%')
            """, nativeQuery = true)
    int softDeleteSubtree(@Param("workspaceId") UUID workspaceId,
                          @Param("artifactId") UUID artifactId,
                          @Param("prefix") String prefix,
                          @Param("deletedAt") Instant deletedAt,
                          @Param("deletedBy") UUID deletedBy);

    /** Restores a node and everything trashed underneath it. */
    @Modifying
    @Query(value = """
            UPDATE artifacts
            SET deleted_at = NULL, deleted_by = NULL
            WHERE workspace_id = :workspaceId
              AND deleted_at IS NOT NULL
              AND (id = :artifactId OR path LIKE :prefix || '%')
            """, nativeQuery = true)
    int restoreSubtree(@Param("workspaceId") UUID workspaceId,
                       @Param("artifactId") UUID artifactId,
                       @Param("prefix") String prefix);

    /**
     * Trash listing: only the roots of trashed subtrees (items whose parent is
     * live or absent), so the trash shows what the user deleted, not every
     * descendant.
     */
    @Query("""
            select a from Artifact a
            where a.workspaceId = :workspaceId
              and a.deletedAt is not null
              and (a.path = '' or not exists (
                    select 1 from Artifact p
                    where p.workspaceId = a.workspaceId
                      and p.deletedAt is not null
                      and a.path = concat(p.path, cast(p.id as string), '.')))
            order by a.deletedAt desc
            """)
    Page<Artifact> findTrashRoots(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    /**
     * Ids of artifacts whose retention window elapsed, honouring the
     * per-workspace override in settings and falling back to the global default.
     */
    @Query(value = """
            SELECT a.id FROM artifacts a
            JOIN workspaces w ON w.id = a.workspace_id
            WHERE a.deleted_at IS NOT NULL
              AND a.deleted_at < now() - make_interval(days =>
                    COALESCE(w.trash_retention_days, :defaultDays))
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findPurgeableIds(@Param("defaultDays") int defaultDays,
                                @Param("batchSize") int batchSize);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    long countByWorkspaceIdAndDeletedAtIsNotNull(UUID workspaceId);

    /** Ids of the live items, parents before their children (shorter paths first). */
    @Query("""
            select a.id from Artifact a
            where a.workspaceId = :workspaceId and a.deletedAt is null
            order by length(a.path), a.id
            """)
    List<UUID> findLiveIdsParentsFirst(@Param("workspaceId") UUID workspaceId);

    @Query("select a.id from Artifact a where a.workspaceId = :workspaceId")
    List<UUID> findAllIdsByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Modifying
    @Query("delete from Artifact a where a.id in :ids")
    int deleteAllByIdIn(@Param("ids") Collection<UUID> ids);

    /** Every live template of a workspace, wherever it sits in the hierarchy. */
    @Query("""
            select t from TemplateArtifact t
            where t.workspaceId = :workspaceId and t.deletedAt is null
            order by t.name asc
            """)
    List<com.sophia.documentworkspace.domain.entity.TemplateArtifact> findTemplates(
            @Param("workspaceId") UUID workspaceId);

    /** Live references to a storage object (duplicated artifacts share keys). */
    @Query("select count(f) from FileArtifact f where f.storageKey = :storageKey")
    long countByStorageKey(@Param("storageKey") String storageKey);

    /** Storage accounting for quota enforcement. */
    @Query("""
            select coalesce(sum(f.sizeBytes), 0) from FileArtifact f
            where f.workspaceId = :workspaceId and f.deletedAt is null
            """)
    long sumFileSizes(@Param("workspaceId") UUID workspaceId);
}
