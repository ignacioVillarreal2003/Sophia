package com.sophia.ai.infrastructure.lock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DistributedLocksTest {

    private static final Duration TTL = Duration.ofMinutes(1);

    @Test
    void aLockHasOneHolderUntilItIsReleased() {
        DistributedLocks locks = new DistributedLocks(null);
        assertThat(locks.tryLock("a", TTL)).isTrue();
        assertThat(locks.tryLock("a", TTL)).isFalse();
        assertThat(locks.tryLock("b", TTL)).isTrue();
        locks.unlock("a");
        assertThat(locks.tryLock("a", TTL)).isTrue();
    }

    @Test
    void slotsAreCountedAndGivenBack() {
        DistributedLocks locks = new DistributedLocks(null);
        assertThat(locks.tryAcquireSlot("u", 2, TTL)).isTrue();
        assertThat(locks.tryAcquireSlot("u", 2, TTL)).isTrue();
        assertThat(locks.tryAcquireSlot("u", 2, TTL)).isFalse();
        assertThat(locks.tryAcquireSlot("other", 2, TTL)).isTrue();
        locks.releaseSlot("u");
        assertThat(locks.tryAcquireSlot("u", 2, TTL)).isTrue();
        locks.releaseSlot("u");
        locks.releaseSlot("u");
        locks.releaseSlot("u"); // releasing more than was taken does not go negative
        assertThat(locks.tryAcquireSlot("u", 1, TTL)).isTrue();
        assertThat(locks.tryAcquireSlot("u", 1, TTL)).isFalse();
    }

    @Test
    void whenRedisIsDownTheyFallBackToThisInstanceInsteadOfRefusingEverything() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), any(java.util.List.class), anyString(),
                anyString())).thenThrow(new IllegalStateException("redis down"));
        DistributedLocks locks = new DistributedLocks(redis);
        assertThat(locks.tryLock("a", TTL)).isTrue();
        assertThat(locks.tryLock("a", TTL)).isFalse();
        assertThat(locks.tryAcquireSlot("u", 1, TTL)).isTrue();
        assertThat(locks.tryAcquireSlot("u", 1, TTL)).isFalse();
    }
}
