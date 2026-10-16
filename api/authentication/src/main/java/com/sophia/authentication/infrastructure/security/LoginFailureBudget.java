package com.sophia.authentication.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class LoginFailureBudget {

    private static final String KEY_PREFIX = "authentication:login-failures:";

    private static final String LUA_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public LoginFailureBudget(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    public void recordFailure(String ip, Duration window) {
        if (ip == null) {
            return;
        }
        try {
            redisTemplate.execute(script, List.of(KEY_PREFIX + ip), String.valueOf(window.toMillis()));
        } catch (DataAccessException e) {
            log.error("Failed to record login failure for ip={}; failing open", ip, e);
        }
    }

    public long failureCount(String ip) {
        if (ip == null) {
            return 0;
        }
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + ip);
            return value == null ? 0 : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException e) {
            log.error("Login failure lookup failed for ip={}; failing open", ip, e);
            return 0;
        }
    }
}
