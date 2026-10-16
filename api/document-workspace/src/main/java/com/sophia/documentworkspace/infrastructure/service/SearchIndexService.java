package com.sophia.documentworkspace.infrastructure.service;

import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.exception.ApiException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Highlight;
import org.opensearch.client.opensearch.core.search.HighlightField;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenSearch gateway: index lifecycle, document upserts/deletes and the
 * full-text query. The index schema mirrors {@link IndexedArtifact}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final OpenSearchClient client;
    private final ApplicationProperties properties;

    /** Flat index document; content is the concatenated block/preview text. */
    public record IndexedArtifact(
            String artifactId,
            String workspaceId,
            String type,
            String name,
            String content,
            String comments,
            List<String> tags,
            String ownerId,
            String mimeType,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SearchHitResult(
            UUID artifactId,
            String name,
            String type,
            double score,
            List<String> highlights
    ) {
    }

    public record SearchResults(long total, List<SearchHitResult> hits) {
    }

    public String indexName() {
        return properties.search().indexPrefix() + "-artifacts";
    }

    /** Creates the index with explicit mappings on startup when it does not exist. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        try {
            boolean exists = client.indices().exists(b -> b.index(indexName())).value();
            if (exists) {
                return;
            }
            client.indices().create(create -> create
                    .index(indexName())
                    .mappings(mappings -> mappings
                            .properties("artifactId", keyword())
                            .properties("workspaceId", keyword())
                            .properties("type", keyword())
                            .properties("name", Property.of(p -> p.text(t -> t)))
                            .properties("content", Property.of(p -> p.text(t -> t)))
                            .properties("comments", Property.of(p -> p.text(t -> t)))
                            .properties("tags", Property.of(p -> p.text(t -> t
                                    .fields("raw", f -> f.keyword(k -> k)))))
                            .properties("ownerId", keyword())
                            .properties("mimeType", keyword())
                            .properties("createdAt", Property.of(p -> p.date(d -> d)))
                            .properties("updatedAt", Property.of(p -> p.date(d -> d)))));
            log.info("Created search index {}", indexName());
        } catch (IOException | RuntimeException e) {
            // The service stays up without search; indexing calls will log failures.
            log.error("Could not ensure search index {}: {}", indexName(), e.getMessage());
        }
    }

    public void index(IndexedArtifact document) {
        try {
            client.index(IndexRequest.of(request -> request
                    .index(indexName())
                    .id(document.artifactId())
                    .document(document)));
        } catch (IOException e) {
            log.error("Failed to index artifact {}: {}", document.artifactId(), e.getMessage());
        }
    }

    public void delete(UUID artifactId) {
        try {
            client.delete(DeleteRequest.of(request -> request
                    .index(indexName())
                    .id(artifactId.toString())));
        } catch (IOException e) {
            log.error("Failed to delete artifact {} from index: {}", artifactId, e.getMessage());
        }
    }

    public void deleteByWorkspace(UUID workspaceId) {
        try {
            client.deleteByQuery(DeleteByQueryRequest.of(request -> request
                    .index(indexName())
                    .query(Query.of(q -> q.term(t -> t
                            .field("workspaceId")
                            .value(FieldValue.of(workspaceId.toString())))))));
        } catch (IOException e) {
            log.error("Failed to purge workspace {} from index: {}", workspaceId, e.getMessage());
        }
    }

    /**
     * Full-text search scoped to one workspace, optionally narrowed by artifact types and tags.
     */
    @Retry(name = "opensearch")
    @CircuitBreaker(name = "opensearch")
    public SearchResults search(UUID workspaceId, String queryText,
                                List<String> types, List<String> tags, int limit, int offset) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .must(m -> m.multiMatch(mm -> mm
                        .query(queryText)
                        .fields("name^3", "tags^2", "content", "comments")
                        .fuzziness("AUTO")))
                .filter(f -> f.term(t -> t.field("workspaceId")
                        .value(FieldValue.of(workspaceId.toString()))));
        if (types != null && !types.isEmpty()) {
            bool.filter(f -> f.terms(t -> t.field("type").terms(terms -> terms
                    .value(types.stream().map(FieldValue::of).toList()))));
        }
        if (tags != null && !tags.isEmpty()) {
            bool.filter(f -> f.terms(t -> t.field("tags.raw").terms(terms -> terms
                    .value(tags.stream().map(FieldValue::of).toList()))));
        }

        SearchRequest request = SearchRequest.of(search -> search
                .index(indexName())
                .query(Query.of(q -> q.bool(bool.build())))
                .from(offset)
                .size(limit)
                .highlight(Highlight.of(h -> h
                        .fields("name", HighlightField.of(f -> f))
                        .fields("content", HighlightField.of(f -> f
                                .fragmentSize(160).numberOfFragments(3))))));
        try {
            SearchResponse<IndexedArtifact> response = client.search(request, IndexedArtifact.class);
            List<SearchHitResult> hits = new ArrayList<>();
            for (Hit<IndexedArtifact> hit : response.hits().hits()) {
                IndexedArtifact source = hit.source();
                if (source == null) {
                    continue;
                }
                List<String> highlights = new ArrayList<>();
                Map<String, List<String>> highlightMap = hit.highlight();
                if (highlightMap != null) {
                    highlightMap.values().forEach(highlights::addAll);
                }
                hits.add(new SearchHitResult(UUID.fromString(source.artifactId()), source.name(),
                        source.type(), hit.score() == null ? 0 : hit.score(), highlights));
            }
            long total = response.hits().total() == null ? hits.size()
                    : response.hits().total().value();
            return new SearchResults(total, hits);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.SEARCH_UNAVAILABLE,
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Search is temporarily unavailable", Map.of(), e);
        }
    }

    private Property keyword() {
        return Property.of(p -> p.keyword(k -> k));
    }
}
