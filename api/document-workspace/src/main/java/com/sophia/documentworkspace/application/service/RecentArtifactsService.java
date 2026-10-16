package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user "recently opened" tracking backed by a Redis sorted set scored by
 * access time. Purely a UX signal: entries expire with the key TTL and the
 * list is re-filtered against live artifacts on read.
 */
@Service
@RequiredArgsConstructor
public class RecentArtifactsService {

    private static final int MAX_RECENTS = 50;
    private static final Duration RECENTS_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    public void recordAccess(UUID userId, UUID workspaceId, UUID artifactId) {
        String key = key(userId, workspaceId);
        redisTemplate.opsForZSet().add(key, artifactId.toString(), System.currentTimeMillis());
        // Cap the set: drop everything below the newest MAX_RECENTS entries.
        redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_RECENTS + 1));
        redisTemplate.expire(key, RECENTS_TTL);
    }

    /** Most recent artifact ids, newest first. */
    public List<UUID> recentArtifactIds(UUID userId, UUID workspaceId, int limit) {
        Set<String> ids = redisTemplate.opsForZSet()
                .reverseRange(key(userId, workspaceId), 0, Math.max(0, limit - 1));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(UUID::fromString).toList();
    }

    public void remove(UUID userId, UUID workspaceId, UUID artifactId) {
        redisTemplate.opsForZSet().remove(key(userId, workspaceId), artifactId.toString());
    }

    private String key(UUID userId, UUID workspaceId) {
        return CacheNames.CACHE_PREFIX + CacheNames.RECENT_DOCUMENTS + ":" + workspaceId + ":" + userId;
    }
}
