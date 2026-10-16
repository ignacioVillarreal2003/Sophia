package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentLockService {

    public static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> EXTEND_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ArtifactAccessService artifactAccess;

    public record LockStatus(boolean locked, UUID holderUserId, boolean heldByMe) {
    }

    public LockStatus acquire(AuthenticatedUser user, UUID workspaceId, UUID documentId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key(documentId), user.id().toString(), LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return new LockStatus(true, user.id(), true);
        }
        UUID holder = currentHolder(documentId).orElse(null);
        if (user.id().equals(holder)) {
            redisTemplate.expire(key(documentId), LOCK_TTL);
            return new LockStatus(true, user.id(), true);
        }
        throw new ConflictException(ErrorCode.ARTIFACT_LOCKED,
                "Document is locked by another user",
                Map.of("holderUserId", String.valueOf(holder)));
    }

    public void release(AuthenticatedUser user, UUID workspaceId, UUID documentId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        Long released = redisTemplate.execute(RELEASE_SCRIPT,
                List.of(key(documentId)), user.id().toString());
        if (released == null || released == 0) {
            Optional<UUID> holder = currentHolder(documentId);
            if (holder.isPresent()) {
                throw new ConflictException(ErrorCode.ARTIFACT_LOCKED,
                        "Only the lock holder can release this lock");
            }
        }
    }

    public LockStatus extend(AuthenticatedUser user, UUID workspaceId, UUID documentId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        Long extended = redisTemplate.execute(EXTEND_SCRIPT,
                List.of(key(documentId)), user.id().toString(),
                String.valueOf(LOCK_TTL.toMillis()));
        if (extended == null || extended == 0) {
            throw new ConflictException(ErrorCode.ARTIFACT_LOCKED,
                    "You do not hold the lock on this document");
        }
        return new LockStatus(true, user.id(), true);
    }

    public LockStatus status(AuthenticatedUser user, UUID workspaceId, UUID documentId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.READ);
        return currentHolder(documentId)
                .map(holder -> new LockStatus(true, holder, holder.equals(user.id())))
                .orElseGet(() -> new LockStatus(false, null, false));
    }

    public void ensureNotLockedByOther(UUID documentId, UUID userId) {
        currentHolder(documentId).ifPresent(holder -> {
            if (!holder.equals(userId)) {
                throw new ConflictException(ErrorCode.ARTIFACT_LOCKED,
                        "Document is locked for exclusive editing by another user",
                        Map.of("holderUserId", holder.toString()));
            }
        });
    }

    private Optional<UUID> currentHolder(UUID documentId) {
        String value = redisTemplate.opsForValue().get(key(documentId));
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    private String key(UUID documentId) {
        return CacheNames.CACHE_PREFIX + "doc-lock:" + documentId;
    }
}
