package com.sophia.authentication.infrastructure.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }

    private void stubCount(Long count) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(count);
    }

    @Test
    @DisplayName("requests within the limit are allowed with the remaining budget")
    void allowsWithinLimit() {
        stubCount(3L);

        RedisRateLimiter.Result result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(7);
    }

    @Test
    @DisplayName("requests over the limit are rejected with a retry hint")
    void rejectsOverLimit() {
        stubCount(11L);

        RedisRateLimiter.Result result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("fails open when Redis is unavailable")
    void failsOpenOnRedisOutage() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new QueryTimeoutException("redis timeout"));

        RedisRateLimiter.Result result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("fails open when the script returns no count")
    void failsOpenOnNullCount() {
        stubCount(null);

        RedisRateLimiter.Result result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(10);
    }

    @Test
    @DisplayName("prefixes every key with the service namespace")
    void namespacesKeys() {
        when(redisTemplate.execute(any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("authentication:rate-limit:bucket:1.2.3.4")), anyString()))
                .thenReturn(1L);

        RedisRateLimiter.Result result = rateLimiter.tryConsume("bucket:1.2.3.4", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
    }
}
