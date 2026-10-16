package com.sophia.authentication.infrastructure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "authentication:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Instant tokenExpiration) {
        Duration ttl = Duration.between(Instant.now(), tokenExpiration);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (DataAccessException e) {
            log.error("Failed to blacklist token jti={}", jti, e);
        }
    }

    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (DataAccessException e) {
            log.error("Blacklist lookup failed for jti={}; failing open", jti, e);
            return false;
        }
    }
}
