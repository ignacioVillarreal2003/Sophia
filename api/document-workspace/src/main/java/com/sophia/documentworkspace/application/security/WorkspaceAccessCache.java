package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.infrastructure.config.CacheNames;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Loads and caches {@link WorkspaceAccessSnapshot}s in Redis. Membership
 * mutations must evict the affected entries; workspace-level changes evict
 * the whole workspace via a SCAN over the key space.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccessCache {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Returns the access snapshot, or null when the workspace does not exist
     * (or is soft-deleted). Nulls are never cached, so a workspace created
     * right after a miss is visible immediately.
     */
    @Cacheable(cacheNames = CacheNames.PERMISSIONS, key = "#workspaceId + ':' + #userId",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public WorkspaceAccessSnapshot load(UUID workspaceId, UUID userId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId).orElse(null);
        if (workspace == null) {
            return null;
        }
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(member -> new WorkspaceAccessSnapshot(
                        workspaceId,
                        workspace.getStatus(),
                        workspace.getOwnerId(),
                        member.getId(),
                        userId,
                        member.getRole(),
                        member.getStatus()))
                .orElseGet(() -> WorkspaceAccessSnapshot.nonMember(
                        workspaceId, workspace.getStatus(), workspace.getOwnerId(), userId));
    }

    @CacheEvict(cacheNames = CacheNames.PERMISSIONS, key = "#workspaceId + ':' + #userId")
    public void evict(UUID workspaceId, UUID userId) {
        // Annotation-driven eviction; intentionally empty.
    }

    /** Evicts every cached snapshot of a workspace (status change, deletion, transfer). */
    public void evictWorkspace(UUID workspaceId) {
        String pattern = CacheNames.CACHE_PREFIX + CacheNames.PERMISSIONS + "::" + workspaceId + ":*";
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
