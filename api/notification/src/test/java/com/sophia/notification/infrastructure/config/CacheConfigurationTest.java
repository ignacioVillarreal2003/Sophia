package com.sophia.notification.infrastructure.config;

import com.sophia.notification.domain.entity.NotificationCategoryView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigurationTest {

    private final GenericJackson2JsonRedisSerializer serializer = CacheConfiguration.cacheValueSerializer();

    @Test
    void categoryViewsSurviveARoundTrip() {
        NotificationCategoryView category =
                new NotificationCategoryView("SYSTEM", "System", "Platform notices", false, true);

        assertThat(serializer.deserialize(serializer.serialize(category))).isEqualTo(category);
    }

    @Test
    void listsOfCategoryViewsSurviveARoundTrip() {
        List<NotificationCategoryView> categories = new ArrayList<>(List.of(
                new NotificationCategoryView("AUTHENTICATION_SECURITY", "Security", null, true, true),
                new NotificationCategoryView("SYSTEM", "System", null, false, true)));

        assertThat(serializer.deserialize(serializer.serialize(categories))).isEqualTo(categories);
    }

    @Test
    void javaTimeValuesSurviveARoundTrip() {
        Instant now = Instant.parse("2026-07-27T12:00:00Z");

        assertThat(serializer.deserialize(serializer.serialize(now))).isEqualTo(now);
    }
}
