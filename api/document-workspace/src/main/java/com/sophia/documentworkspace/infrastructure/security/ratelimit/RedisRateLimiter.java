package com.sophia.documentworkspace.infrastructure.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class RedisRateLimiter {

    private static final String LUA_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    public Result tryConsume(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.execute(script, List.of("document-workspace:rate-limit:" + key),
                    String.valueOf(window.toMillis()));
            if (count == null) {
                return Result.allowed(limit);
            }
            long remaining = Math.max(0, limit - count);
            return count <= limit ? Result.allowed(remaining) : Result.rejected(window);
        } catch (RuntimeException e) {
            log.error("Rate limiter unavailable for key={}; failing open", key, e);
            return Result.allowed(limit);
        }
    }

    public record Result(boolean allowed, long remaining, long retryAfterSeconds) {

        static Result allowed(long remaining) {
            return new Result(true, remaining, 0);
        }

        static Result rejected(Duration window) {
            return new Result(false, 0, Math.max(1, window.toSeconds()));
        }
    }
}
