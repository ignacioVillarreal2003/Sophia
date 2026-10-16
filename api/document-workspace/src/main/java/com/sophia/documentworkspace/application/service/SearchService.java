package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.response.SearchResponse;
import com.sophia.documentworkspace.infrastructure.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Search facade: membership check, search (executed inside the
 * search engine as a rank range) and result shaping. Never uses SQL LIKE.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final SearchIndexService searchIndexService;
    private final WorkspaceAuthorizationService workspaceAuthorization;

    public SearchResponse search(AuthenticatedUser user, UUID workspaceId, String query,
                                 List<String> types, List<String> tags, int limit, int offset) {
        workspaceAuthorization.requireActiveMember(workspaceId, user);
        SearchIndexService.SearchResults results = searchIndexService.search(
                workspaceId, query, types, tags, limit, offset);
        List<SearchResponse.SearchHit> hits = results.hits().stream()
                .map(hit -> new SearchResponse.SearchHit(hit.artifactId(), hit.name(), hit.type(),
                        hit.score(), hit.highlights()))
                .toList();
        return new SearchResponse(query, results.total(), limit, offset, hits);
    }
}
