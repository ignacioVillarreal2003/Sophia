package com.sophia.authentication.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginFailureBudgetTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private LoginFailureBudget budget;

    @BeforeEach
    void setUp() {
        budget = new LoginFailureBudget(redisTemplate);
    }

    @Test
    @DisplayName("recording a failure increments the windowed counter")
    void recordsFailure() {
        budget.recordFailure("10.0.0.9", Duration.ofMinutes(15));

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("authentication:login-failures:10.0.0.9")), eq("900000"));
    }

    @Test
    @DisplayName("reads the current failure count")
    void readsFailureCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("authentication:login-failures:10.0.0.9")).thenReturn("7");

        assertThat(budget.failureCount("10.0.0.9")).isEqualTo(7);
    }

    @Test
    @DisplayName("a missing counter reads as zero")
    void missingCounterIsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(budget.failureCount("10.0.0.9")).isZero();
    }

    @Test
    @DisplayName("fails open when Redis is unavailable")
    void failsOpenOnRedisOutage() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new QueryTimeoutException("down"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new QueryTimeoutException("down"));

        assertThatCode(() -> budget.recordFailure("10.0.0.9", Duration.ofMinutes(15)))
                .doesNotThrowAnyException();
        assertThat(budget.failureCount("10.0.0.9")).isZero();
    }

    @Test
    @DisplayName("null IPs are ignored")
    void ignoresNullIp() {
        budget.recordFailure(null, Duration.ofMinutes(15));

        assertThat(budget.failureCount(null)).isZero();
    }
}
