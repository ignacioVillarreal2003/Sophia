package com.sophia.documentworkspace.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.domain.dto.realtime.PresenceUser;
import com.sophia.documentworkspace.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Active-collaborator tracking in Redis, keyed per document, plus a per-
 * WebSocket-session index so a dropped connection cleans up every document it
 * was viewing. Redis makes presence consistent across service instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration PRESENCE_TTL = Duration.ofHours(12);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PresenceUser join(String sessionId, UUID workspaceId, UUID documentId,
                             UUID userId, String email) {
        PresenceUser user = new PresenceUser(userId, email, Instant.now());
        redisTemplate.opsForHash().put(presenceKey(documentId), userId.toString(), serialize(user));
        redisTemplate.expire(presenceKey(documentId), PRESENCE_TTL);
        redisTemplate.opsForSet().add(sessionKey(sessionId),
                workspaceId + "|" + documentId + "|" + userId + "|" + (email == null ? "" : email));
        redisTemplate.expire(sessionKey(sessionId), PRESENCE_TTL);
        return user;
    }

    public void leave(UUID documentId, UUID userId) {
        redisTemplate.opsForHash().delete(presenceKey(documentId), userId.toString());
    }

    /** Documents a session was attached to: {@code workspaceId|documentId|userId|email}. */
    public Set<String> sessionAttachments(String sessionId) {
        Set<String> members = redisTemplate.opsForSet().members(sessionKey(sessionId));
        return members == null ? Set.of() : members;
    }

    public void clearSession(String sessionId) {
        redisTemplate.delete(sessionKey(sessionId));
    }

    public List<PresenceUser> activeUsers(UUID documentId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(presenceKey(documentId));
        List<PresenceUser> users = new ArrayList<>();
        for (Object value : entries.values()) {
            PresenceUser user = deserialize(value.toString());
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    private String presenceKey(UUID documentId) {
        return CacheNames.CACHE_PREFIX + "presence:" + documentId;
    }

    private String sessionKey(String sessionId) {
        return CacheNames.CACHE_PREFIX + "ws-session:" + sessionId;
    }

    private String serialize(PresenceUser user) {
        try {
            return objectMapper.writeValueAsString(user);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize presence entry", e);
        }
    }

    private PresenceUser deserialize(String json) {
        try {
            return objectMapper.readValue(json, PresenceUser.class);
        } catch (JsonProcessingException e) {
            log.warn("Dropping corrupt presence entry: {}", json);
            return null;
        }
    }
}
