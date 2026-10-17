package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceImpactResponse;
import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import com.sophia.documentworkspace.infrastructure.repository.AccessRequestRepository;
import com.sophia.documentworkspace.infrastructure.repository.PublicLinkRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceImpactService {

    private final NamedParameterJdbcTemplate jdbc;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceMemberRepository memberRepository;
    private final PublicLinkRepository publicLinkRepository;
    private final AccessRequestRepository accessRequestRepository;

    @Transactional(readOnly = true)
    public WorkspaceImpactResponse impact(AuthenticatedUser user, UUID workspaceId) {
        authorization.requireOwner(workspaceId, user);
        long[] counts = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE kind = 'FOLDER'),
                       COUNT(*) FILTER (WHERE kind = 'DOCUMENT'),
                       COUNT(*) FILTER (WHERE kind = 'FILE'),
                       COALESCE(SUM(size_bytes) FILTER (WHERE kind = 'FILE'), 0)
                FROM artifacts WHERE workspace_id = :id AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", workspaceId),
                (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)});
        return new WorkspaceImpactResponse(counts[0], counts[1], counts[2], counts[3],
                memberRepository.countByWorkspaceId(workspaceId),
                publicLinkRepository.countActive(workspaceId, Instant.now()),
                accessRequestRepository.countByWorkspaceIdAndStatus(workspaceId, RequestStatus.PENDING));
    }
}
