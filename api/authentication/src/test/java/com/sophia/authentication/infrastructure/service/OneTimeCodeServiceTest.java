package com.sophia.authentication.infrastructure.service;

import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OneTimeCodeServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private OneTimeCodeService oneTimeCodeService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        oneTimeCodeService = new OneTimeCodeService(redisTemplate, TestFixtures.appProperties());
    }

    @Test
    @DisplayName("issuing stores the user id under the code with the configured TTL")
    void issueStoresCode() {
        UUID userId = UUID.randomUUID();

        String code = oneTimeCodeService.issue(userId);

        assertThat(code).isNotBlank();
        verify(valueOperations).set(startsWith("authentication:one-time-code:"),
                eq(userId.toString()), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("consuming a valid code returns the user id exactly once")
    void consumeReturnsUserId() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.getAndDelete("authentication:one-time-code:code-1"))
                .thenReturn(userId.toString());

        assertThat(oneTimeCodeService.consume("code-1")).isEqualTo(userId);
    }

    @Test
    @DisplayName("consuming an unknown or expired code fails")
    void consumeRejectsUnknownCode() {
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        assertThatThrownBy(() -> oneTimeCodeService.consume("expired"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
