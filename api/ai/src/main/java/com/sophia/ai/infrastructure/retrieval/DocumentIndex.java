package com.sophia.ai.infrastructure.retrieval;

import com.sophia.ai.application.retrieval.TextAnalyzer;
import com.sophia.ai.domain.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full-text search over the documents used in conversations, in the database the service already has. A document is
 * split into passages once and re-split only when its text changes (compared by hash), so asking ten questions about
 * the same notes indexes them once.
 *
 * <p>This is lexical search, chosen over embeddings on purpose: the model provider offers no embedding model, and a
 * second vendor, a vector extension and a re-embedding job are a lot of moving parts for notes a person wrote
 * themselves, whose questions tend to reuse the notes' own words. The {@code search} contract does not care how
 * passages are ranked, so a vector index can replace this class without touching the callers.
 *
 * <p>Nothing here checks who may read a document; callers must have loaded the text with the person's own token
 * before they index or search it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIndex {

    /** One passage of a document, as handed to the prompt. */
    public record Passage(UUID artifactId, int position, String content, double rank) {
    }

    private final JdbcTemplate jdbc;
    private final ApplicationProperties properties;

    /** Makes sure the stored passages match {@code text}; returns true when it had to re-index. */
    @Transactional
    public boolean ensureIndexed(UUID workspaceId, UUID artifactId, String text) {
        String hash = sha256(text);
        // Two people asking about the same document at once must not index it twice over each other.
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
        }, artifactId.toString());
        List<String> current = jdbc.query("select content_hash from ai_doc_index where artifact_id = ?",
                (rs, i) -> rs.getString(1), artifactId);
        if (!current.isEmpty() && current.get(0).equals(hash)) {
            return false;
        }
        List<String> chunks = TextAnalyzer.chunks(text, properties.ai().chat().chunkChars());
        jdbc.update("delete from ai_doc_index where artifact_id = ?", artifactId);
        jdbc.update("insert into ai_doc_index (artifact_id, workspace_id, content_hash, chunk_count, indexed_at)"
                + " values (?, ?, ?, ?, now())", artifactId, workspaceId, hash, chunks.size());
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            rows.add(new Object[]{artifactId, i, chunks.get(i), TextAnalyzer.indexable(chunks.get(i))});
        }
        jdbc.batchUpdate("insert into ai_doc_chunks (artifact_id, position, content, tsv)"
                + " values (?, ?, ?, to_tsvector('simple', ?))", rows);
        log.debug("Indexed {} in {} passages", artifactId, chunks.size());
        return true;
    }

    /**
     * The best passages for a question among the given documents, best first. When the question has no usable words
     * (it was all stop words) it falls back to the opening passages so the model still gets something to work with.
     */
    @Transactional(readOnly = true)
    public List<Passage> search(List<UUID> artifactIds, String question, int limit) {
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        String ids = artifactIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<String> terms = TextAnalyzer.queryTerms(question);
        List<Object> args = new ArrayList<>();
        String sql;
        if (terms.isEmpty()) {
            sql = "select artifact_id, position, content, 0.0 as rank from ai_doc_chunks where artifact_id in (" + ids
                    + ") order by position, artifact_id limit ?";
        } else {
            // Terms are letters and digits only (see TextAnalyzer), so they are safe inside a tsquery.
            String query = terms.stream().map(t -> t.replaceAll("[^\\p{L}\\p{N}]", "")).filter(t -> !t.isEmpty())
                    .collect(Collectors.joining(" | "));
            sql = "select artifact_id, position, content, ts_rank_cd(tsv, q, 32) as rank from ai_doc_chunks,"
                    + " to_tsquery('simple', ?) q where artifact_id in (" + ids + ") and tsv @@ q"
                    + " order by rank desc, position limit ?";
            args.add(query);
        }
        args.addAll(artifactIds);
        args.add(limit);
        return jdbc.query(sql, (rs, i) -> new Passage((UUID) rs.getObject(1), rs.getInt(2), rs.getString(3),
                rs.getDouble(4)), args.toArray());
    }

    /** Passages of an index nobody has asked about for a while are dropped; they are rebuilt on demand. */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeIdle() {
        Instant cutoff = Instant.now().minus(properties.ai().chat().retention());
        int removed = jdbc.update("delete from ai_doc_index where indexed_at < ?", Timestamp.from(cutoff));
        if (removed > 0) {
            log.info("Dropped the search index of {} idle documents", removed);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
