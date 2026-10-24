package com.sophia.ai.infrastructure.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The two small pieces of coordination the service needs between its instances: "only one answer at a time in this
 * conversation" and "at most N generations running for this person". Both live in Redis with an expiry, so a crashed
 * instance cannot hold anything for longer than the expiry. When Redis cannot be reached they fall back to counting
 * inside this instance (a weaker guarantee, still bounded by the number of instances), because refusing every
 * generation over a cache outage would be the worse failure.
 */
@Slf4j
@Component
public class DistributedLocks {

    private static final String ACQUIRE_SLOT = """
            local n = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            if n > tonumber(ARGV[1]) then
                redis.call('DECR', KEYS[1])
                return 0
            end
            return 1
            """;

    private static final String RELEASE_SLOT = """
            local n = redis.call('DECR', KEYS[1])
            if n <= 0 then
                redis.call('DEL', KEYS[1])
            end
            return n
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> acquireSlot = new DefaultRedisScript<>(ACQUIRE_SLOT, Long.class);
    private final DefaultRedisScript<Long> releaseSlot = new DefaultRedisScript<>(RELEASE_SLOT, Long.class);
    private final Set<String> localKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> localSlots = new ConcurrentHashMap<>();

    @Autowired
    public DistributedLocks(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** True when the key was free and is now held; it frees itself after {@code ttl} if nobody releases it. */
    public boolean tryLock(String key, Duration ttl) {
        if (redis != null) {
            try {
                return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("ai:lock:" + key, "1", ttl));
            } catch (RuntimeException e) {
                log.warn("Redis unavailable for lock {}; using a local lock", key, e);
            }
        }
        return localKeys.add(key);
    }

    public void unlock(String key) {
        localKeys.remove(key);
        if (redis != null) {
            try {
                redis.delete("ai:lock:" + key);
            } catch (RuntimeException e) {
                log.warn("Could not release lock {}; it expires on its own", key, e);
            }
        }
    }

    /** Takes one of {@code max} slots under {@code key}; false when they are all taken. */
    public boolean tryAcquireSlot(String key, int max, Duration ttl) {
        if (redis != null) {
            try {
                Long taken = redis.execute(acquireSlot, List.of("ai:slots:" + key), String.valueOf(max),
                        String.valueOf(ttl.toMillis()));
                return taken != null && taken == 1L;
            } catch (RuntimeException e) {
                log.warn("Redis unavailable for slots {}; counting locally", key, e);
            }
        }
        AtomicInteger counter = localSlots.computeIfAbsent(key, k -> new AtomicInteger());
        if (counter.incrementAndGet() > max) {
            counter.decrementAndGet();
            return false;
        }
        return true;
    }

    public void releaseSlot(String key) {
        AtomicInteger counter = localSlots.get(key);
        if (counter != null && counter.decrementAndGet() <= 0) {
            localSlots.remove(key, counter);
        }
        if (redis != null) {
            try {
                redis.execute(releaseSlot, List.of("ai:slots:" + key));
            } catch (RuntimeException e) {
                log.warn("Could not release slot {}; it expires on its own", key, e);
            }
        }
    }
}
