package com.sophia.notification.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyStore {

    public record CachedResponse(int status, String contentType, byte[] body) {
    }

    private static final String RESPONSE_PREFIX = "notification:idempotency:response:";
    private static final String LOCK_PREFIX = "notification:idempotency:lock:";
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<CachedResponse> find(String key) {
        try {
            String json = redisTemplate.opsForValue().get(RESPONSE_PREFIX + key);
            return json == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(json, CachedResponse.class));
        } catch (Exception e) {
            log.error("Idempotency lookup failed; failing open", e);
            return Optional.empty();
        }
    }

    public boolean tryLock(String key) {
        try {
            return !Boolean.FALSE.equals(
                    redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + key, "1", LOCK_TTL));
        } catch (DataAccessException e) {
            log.error("Idempotency lock failed; failing open", e);
            return true;
        }
    }

    public void unlock(String key) {
        try {
            redisTemplate.delete(LOCK_PREFIX + key);
        } catch (DataAccessException e) {
            log.debug("Idempotency unlock failed for key {}", key, e);
        }
    }

    public void save(String key, CachedResponse response) {
        try {
            redisTemplate.opsForValue().set(RESPONSE_PREFIX + key,
                    objectMapper.writeValueAsString(response), RESPONSE_TTL);
        } catch (Exception e) {
            log.error("Failed to store idempotent response", e);
        }
    }
}
