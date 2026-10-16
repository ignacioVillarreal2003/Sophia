package com.sophia.authentication.infrastructure.service;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OneTimeCodeService {

    private static final String KEY_PREFIX = "authentication:one-time-code:";

    private final StringRedisTemplate redisTemplate;
    private final ApplicationProperties properties;

    public String issue(UUID userId) {
        String code = TokenHasher.generateToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, userId.toString(),
                properties.oauth2().oneTimeCodeTtl());
        return code;
    }

    public UUID consume(String code) {
        String userId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
        if (userId == null) {
            throw new InvalidTokenException("Invalid or expired authorization code");
        }
        return UUID.fromString(userId);
    }
}
