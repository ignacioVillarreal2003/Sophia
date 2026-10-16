package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Full-text search results across a workspace's artifacts")
public record SearchResponse(
        String query,
        long total,
        int limit,
        int offset,
        List<SearchHit> hits
) {

    public record SearchHit(
            UUID artifactId,
            String name,
            String type,
            double score,
            List<String> highlights
    ) {
    }
}
