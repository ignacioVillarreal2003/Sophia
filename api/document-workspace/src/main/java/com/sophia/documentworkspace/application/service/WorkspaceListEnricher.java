package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.dto.response.WorkspaceLabelRef;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceStats;
import com.sophia.documentworkspace.domain.entity.WorkspaceLabel;
import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLabelLinkRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLabelRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adds what the list views need on top of a workspace: the caller's pin, last visit and labels, plus
 * (for the detailed cards) member, content and storage numbers. Everything is fetched in batches for
 * the whole page, never per workspace.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceListEnricher {

    private static final int MEMBER_PREVIEW = 4;

    private final NamedParameterJdbcTemplate jdbc;
    private final WorkspaceLimitsRepository limitsRepository;
    private final WorkspaceLabelRepository labelRepository;
    private final WorkspaceLabelLinkRepository labelLinkRepository;

    public Map<UUID, List<WorkspaceLabelRef>> labelsFor(UUID userId, Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, WorkspaceLabel> labels = labelRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(WorkspaceLabel::getId, Function.identity()));
        Map<UUID, List<WorkspaceLabelRef>> byWorkspace = new HashMap<>();
        for (var link : labelLinkRepository.findForUser(userId, workspaceIds)) {
            WorkspaceLabel label = labels.get(link.getLabelId());
            if (label != null) {
                byWorkspace.computeIfAbsent(link.getWorkspaceId(), k -> new ArrayList<>())
                        .add(new WorkspaceLabelRef(label.getId(), label.getName(), label.getColor()));
            }
        }
        byWorkspace.values().forEach(list -> list.sort((a, b) -> a.name().compareToIgnoreCase(b.name())));
        return byWorkspace;
    }

    public Map<UUID, WorkspaceStats> statsFor(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource ids = new MapSqlParameterSource("ids", workspaceIds);

        Map<UUID, Long> members = new HashMap<>();
        jdbc.query("""
                SELECT workspace_id, COUNT(*) AS n FROM workspace_members
                WHERE workspace_id IN (:ids) AND status = 'ACTIVE' GROUP BY workspace_id
                """, ids, rs -> {
            members.put(rs.getObject("workspace_id", UUID.class), rs.getLong("n"));
        });

        Map<UUID, long[]> content = new HashMap<>();
        jdbc.query("""
                SELECT workspace_id,
                       COUNT(*) FILTER (WHERE kind <> 'FOLDER') AS items,
                       COALESCE(SUM(size_bytes) FILTER (WHERE kind = 'FILE'), 0) AS bytes
                FROM artifacts
                WHERE workspace_id IN (:ids) AND deleted_at IS NULL GROUP BY workspace_id
                """, ids, rs -> {
            content.put(rs.getObject("workspace_id", UUID.class), new long[]{rs.getLong("items"), rs.getLong("bytes")});
        });

        Map<UUID, List<WorkspaceStats.MemberPreview>> previews = new HashMap<>();
        jdbc.query("""
                SELECT workspace_id, user_id, email, display_name FROM (
                    SELECT m.workspace_id, m.user_id, m.email, m.display_name,
                           ROW_NUMBER() OVER (PARTITION BY m.workspace_id
                                              ORDER BY (m.user_id = w.owner_id) DESC, m.joined_at) AS rn
                    FROM workspace_members m JOIN workspaces w ON w.id = m.workspace_id
                    WHERE m.workspace_id IN (:ids) AND m.status = 'ACTIVE') ranked
                WHERE rn <= :limit
                ORDER BY workspace_id, rn
                """, new MapSqlParameterSource("ids", workspaceIds).addValue("limit", MEMBER_PREVIEW), rs -> {
            previews.computeIfAbsent(rs.getObject("workspace_id", UUID.class), k -> new ArrayList<>())
                    .add(new WorkspaceStats.MemberPreview(rs.getObject("user_id", UUID.class),
                            rs.getString("display_name"), rs.getString("email")));
        });

        Map<UUID, Long> quotas = limitsRepository.findAllById(workspaceIds).stream()
                .filter(l -> l.getStorageQuotaMb() != null)
                .collect(Collectors.toMap(WorkspaceLimits::getWorkspaceId, WorkspaceLimits::getStorageQuotaMb));

        Map<UUID, WorkspaceStats> stats = new HashMap<>();
        for (UUID id : workspaceIds) {
            long[] totals = content.getOrDefault(id, new long[]{0, 0});
            stats.put(id, new WorkspaceStats(members.getOrDefault(id, 0L), totals[0], totals[1], quotas.get(id),
                    previews.getOrDefault(id, List.of())));
        }
        return stats;
    }

    /** Fills pin, last visit, labels and (optionally) stats of one page of workspaces. */
    public List<WorkspaceResponse> enrich(UUID userId, List<WorkspaceResponse> page, Map<UUID, WorkspaceMember> memberships,
                                          boolean withStats) {
        List<UUID> ids = page.stream().map(WorkspaceResponse::id).toList();
        Map<UUID, List<WorkspaceLabelRef>> labels = labelsFor(userId, ids);
        Map<UUID, WorkspaceStats> stats = withStats ? statsFor(ids) : Map.of();
        return page.stream().map(response -> {
            WorkspaceMember member = memberships.get(response.id());
            return response.withExtras(
                    member != null && member.isPinned(),
                    member == null ? null : member.getLastAccessedAt(),
                    labels.getOrDefault(response.id(), List.of()),
                    stats.get(response.id()));
        }).toList();
    }

    static Timestamp ts(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
