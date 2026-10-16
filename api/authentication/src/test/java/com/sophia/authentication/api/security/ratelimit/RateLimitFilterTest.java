package com.sophia.authentication.api.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.infrastructure.security.ratelimit.RedisRateLimiter;
import com.sophia.authentication.support.TestFixtures;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private RedisRateLimiter rateLimiter;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;

    private static ApplicationProperties propertiesWithRateLimit(boolean enabled) {
        ApplicationProperties base = TestFixtures.appProperties();
        ApplicationProperties.Security security = base.security();
        return new ApplicationProperties(
                new ApplicationProperties.Security(security.jwt(), security.cors(), security.password(),
                        security.lockout(), security.requireEmailVerification(),
                        security.passwordResetTokenTtl(), security.emailVerificationTokenTtl(),
                        new ApplicationProperties.Security.RateLimit(enabled, 10, Duration.ofMinutes(1),
                                100, Duration.ofMinutes(1))),
                base.oauth2(), base.notifications(), base.storage(), base.monitoring(),
                base.admin(), base.events());
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, propertiesWithRateLimit(true), objectMapper(),
                TestFixtures.authenticationMetrics());
    }

    @Test
    @DisplayName("skips filtering when the rate limit is disabled")
    void skipsWhenDisabled() {
        RateLimitFilter disabled = new RateLimitFilter(rateLimiter, propertiesWithRateLimit(false),
                objectMapper(), TestFixtures.authenticationMetrics());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/authentication/login");
        request.setRequestURI("/api/v1/authentication/login");

        assertThat(disabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("never rate limits infrastructure endpoints")
    void skipsInfrastructurePaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRequestURI("/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("allows requests within the budget and exposes the remaining quota")
    void allowsWithinBudget() throws Exception {
        when(rateLimiter.tryConsume(startsWith("authentication:"), eq(10), any()))
                .thenReturn(new RedisRateLimiter.Result(true, 7, 0));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/authentication/login");
        request.setRequestURI("/api/v1/authentication/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("7");
    }

    @Test
    @DisplayName("rejects requests over the budget with 429 and Retry-After")
    void rejectsOverBudget() throws Exception {
        when(rateLimiter.tryConsume(startsWith("authentication:"), anyInt(), any()))
                .thenReturn(new RedisRateLimiter.Result(false, 0, 60));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/authentication/login");
        request.setRequestURI("/api/v1/authentication/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("Too many requests");
    }

    @Test
    @DisplayName("general endpoints consume the general bucket")
    void generalBucketForOtherPaths() throws Exception {
        when(rateLimiter.tryConsume(startsWith("general:"), eq(100), any()))
                .thenReturn(new RedisRateLimiter.Result(true, 99, 0));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setRequestURI("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
