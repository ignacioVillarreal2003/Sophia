package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.dto.response.ActivityItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * What happened lately across every workspace the caller belongs to, built from the audit trail. Only the events
 * that matter to collaborators are listed, and by default never the caller's own.
 */
@Service
@RequiredArgsConstructor
public class ActivityFeedService {

    private static final List<String> ACTIONS = List.of(
            "artifact.created", "artifact.updated", "artifact.moved", "artifact.duplicated", "artifact.trashed",
            "artifact.restored", "document.content-updated", "document.version-restored", "file.uploaded",
            "file.version-added", "comment.created", "comment.resolved", "workspace.updated",
            "workspace.ownership-transferred", "workspace-member.added", "workspace-member.removed",
            "workspace-member.role-changed");
    private static final int MAX = 100;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ActivityItem> recent(AuthenticatedUser user, int limit, boolean includeOwn) {
        var params = new MapSqlParameterSource()
                .addValue("userId", user.id())
                .addValue("actions", ACTIONS)
                .addValue("limit", Math.min(Math.max(limit, 1), MAX));
        String own = includeOwn ? "" : " AND (a.user_id IS NULL OR a.user_id <> :userId)";
        return jdbc.query("""
                SELECT a.id, w.id AS workspace_id, w.name AS workspace_name, a.action, a.user_id,
                       actor.email AS actor_email, actor.display_name AS actor_name,
                       a.resource_type, a.resource_id, a.details ->> 'name' AS resource_name, a.created_at
                FROM audit_logs a
                JOIN workspaces w ON a.details ->> 'workspaceId' = CAST(w.id AS TEXT)
                JOIN workspace_members m ON m.workspace_id = w.id AND m.user_id = :userId AND m.status = 'ACTIVE'
                LEFT JOIN workspace_members actor ON actor.workspace_id = w.id AND actor.user_id = a.user_id
                WHERE w.deleted_at IS NULL AND a.result = 'SUCCESS' AND a.action IN (:actions)
                """ + own + " ORDER BY a.created_at DESC LIMIT :limit", params, (rs, i) -> new ActivityItem(
                UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("workspace_id")),
                rs.getString("workspace_name"), rs.getString("action"),
                rs.getString("user_id") == null ? null : UUID.fromString(rs.getString("user_id")),
                rs.getString("actor_email"), rs.getString("actor_name"), rs.getString("resource_type"),
                rs.getString("resource_id"), rs.getString("resource_name"),
                rs.getTimestamp("created_at").toInstant()));
    }
}
