package com.sophia.documentworkspace.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new IdempotencyStore(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("a cached response is returned when the key was previously saved")
    void findReturnsCachedResponse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("document-workspace:idempotency:response:key-1"))
                .thenReturn("""
                        {"status":200,"contentType":"application/json","body":"aGk="}""");

        var cached = store.find("key-1").orElseThrow();

        assertThat(cached.status()).isEqualTo(200);
        assertThat(cached.contentType()).isEqualTo("application/json");
        assertThat(new String(cached.body(), StandardCharsets.UTF_8)).isEqualTo("hi");
    }

    @Test
    @DisplayName("an absent key returns empty")
    void findReturnsEmptyWhenMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(store.find("missing")).isEmpty();
    }

    @Test
    @DisplayName("a lookup failure fails open instead of throwing")
    void findFailsOpenOnError() {
        when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

        assertThat(store.find("key-1")).isEmpty();
    }

    @Test
    @DisplayName("the first caller to lock a key succeeds")
    void tryLockSucceedsWhenUnlocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("document-workspace:idempotency:lock:key-1"), eq("1"),
                eq(Duration.ofSeconds(60)))).thenReturn(true);

        assertThat(store.tryLock("key-1")).isTrue();
    }

    @Test
    @DisplayName("a concurrent caller cannot acquire an already-held lock")
    void tryLockFailsWhenAlreadyLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60))))
                .thenReturn(false);

        assertThat(store.tryLock("key-1")).isFalse();
    }

    @Test
    @DisplayName("a lock failure fails open so the request is not blocked")
    void tryLockFailsOpenOnError() {
        when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

        assertThat(store.tryLock("key-1")).isTrue();
    }

    @Test
    @DisplayName("unlocking removes the lock key")
    void unlockDeletesTheLockKey() {
        store.unlock("key-1");

        verify(redisTemplate).delete("document-workspace:idempotency:lock:key-1");
    }

    @Test
    @DisplayName("an unlock failure is swallowed rather than propagated")
    void unlockSwallowsErrors() {
        doThrow(new QueryTimeoutException("redis down"))
                .when(redisTemplate).delete(anyString());

        store.unlock("key-1");

        verify(redisTemplate).delete("document-workspace:idempotency:lock:key-1");
    }

    @Test
    @DisplayName("saving stores the serialized response with the response TTL")
    void saveStoresSerializedResponse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.save("key-1", new IdempotencyStore.CachedResponse(201, "application/json",
                "created".getBytes(StandardCharsets.UTF_8)));

        verify(valueOperations).set(eq("document-workspace:idempotency:response:key-1"),
                anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("a save failure is swallowed rather than propagated")
    void saveSwallowsErrors() {
        when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

        store.save("key-1", new IdempotencyStore.CachedResponse(200, "text/plain", new byte[0]));
    }
}
