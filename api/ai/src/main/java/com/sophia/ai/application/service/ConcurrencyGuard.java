package com.sophia.ai.application.service;

import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.lock.DistributedLocks;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Caps how many generations one person runs at once, across all instances, so a script (or a stuck browser tab)
 * cannot hold a pile of model calls open. A slot that is never released (an instance that died) frees itself after
 * the longest a generation may take.
 */
@Component
@RequiredArgsConstructor
public class ConcurrencyGuard {

    /** Longer than the request timeout plus its retries, so a live generation never loses its slot. */
    private static final Duration SLOT_TTL = Duration.ofMinutes(20);

    private final ApplicationProperties properties;
    private final DistributedLocks locks;

    /** Release it when the generation ends, whatever the outcome. */
    public Lease acquire(UUID userId) {
        int max = properties.ai().maxConcurrentPerUser();
        String key = "gen:" + userId;
        if (!locks.tryAcquireSlot(key, max, SLOT_TTL)) {
            throw new ApiException(ErrorCode.TOO_MANY_CONCURRENT, HttpStatus.TOO_MANY_REQUESTS,
                    "You already have %d generations running. Wait for one to finish.".formatted(max));
        }
        return () -> locks.releaseSlot(key);
    }

    @FunctionalInterface
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
