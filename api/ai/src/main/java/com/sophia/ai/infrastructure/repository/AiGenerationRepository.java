package com.sophia.ai.infrastructure.repository;

import com.sophia.ai.domain.entity.AiGeneration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {

    /** Tokens of every kind a call used: what the quotas count. */
    String TOKENS = "g.inputTokens + g.outputTokens + g.cacheReadTokens + g.cacheWriteTokens";

    @Query("select coalesce(sum(" + TOKENS + "), 0) from AiGeneration g where g.userId = :userId and g.createdAt >= :since")
    long tokensUsedByUser(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("select coalesce(sum(" + TOKENS + "), 0) from AiGeneration g "
            + "where g.workspaceId = :workspaceId and g.createdAt >= :since")
    long tokensUsedInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    interface UserUsage {
        UUID getUserId();

        String getUserEmail();

        long getTokens();

        long getRequests();
    }

    @Query("select g.userId as userId, max(g.userEmail) as userEmail, sum(" + TOKENS + ") as tokens, count(g) as requests "
            + "from AiGeneration g where g.createdAt >= :since group by g.userId order by sum(" + TOKENS + ") desc")
    List<UserUsage> topUsers(@Param("since") Instant since, Pageable pageable);

    interface FeatureUsage {
        String getFeature();

        long getTokens();

        long getRequests();
    }

    @Query("select cast(g.feature as string) as feature, sum(" + TOKENS + ") as tokens, count(g) as requests "
            + "from AiGeneration g where g.createdAt >= :since group by g.feature order by sum(" + TOKENS + ") desc")
    List<FeatureUsage> byFeature(@Param("since") Instant since);

    interface DayUsage {
        String getDay();

        long getTokens();

        long getRequests();
    }

    @Query(value = """
            select to_char(date_trunc('day', created_at at time zone 'UTC'), 'YYYY-MM-DD') as day,
                   coalesce(sum(input_tokens + output_tokens + cache_read_tokens + cache_write_tokens), 0) as tokens,
                   count(*) as requests
            from ai_generations where created_at >= :since
            group by 1 order by 1
            """, nativeQuery = true)
    List<DayUsage> byDay(@Param("since") Instant since);

    @Modifying
    @Query("delete from AiGeneration g where g.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
