package com.sophia.ai;

import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.config.ApplicationProperties.Ai;
import com.sophia.ai.domain.config.ApplicationProperties.Ai.Chat;
import com.sophia.ai.domain.config.ApplicationProperties.Ai.Defaults;
import com.sophia.ai.domain.config.ApplicationProperties.Ai.GenerationRateLimit;
import com.sophia.ai.domain.config.ApplicationProperties.Monitoring;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Builders shared by the unit tests. */
public final class TestSupport {

    private TestSupport() {
    }

    public static Chat chat() {
        return new Chat(60000, 8, 1200, 1500000, 24, 400, 100, Duration.ofDays(180));
    }

    public static Ai ai(String provider, int maxConcurrent) {
        return new Ai(provider, "key", "http://localhost:0/v1beta", "gemini-test", Duration.ofMinutes(5), 2, 120000, 12, maxConcurrent,
                new GenerationRateLimit(20, Duration.ofMinutes(1)), new Defaults(true, 400000, 2000000), chat(), new ApplicationProperties.Ai.Upload(5242880, 15728640, 50),
                "http://localhost:8081", Duration.ofDays(90));
    }

    public static ApplicationProperties properties(String provider, int maxConcurrent) {
        return new ApplicationProperties(null, ai(provider, maxConcurrent), new Monitoring("u", "p"));
    }

    public static AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), "student@example.com", Set.of(), Set.of());
    }

    public static AuthenticatedUser manager() {
        return new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Set.of(), Set.of("WORKSPACE_MANAGE"));
    }
}
