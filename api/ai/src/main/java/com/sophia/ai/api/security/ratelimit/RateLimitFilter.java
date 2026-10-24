package com.sophia.ai.api.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.ai.api.filter.CorrelationIdFilter;
import com.sophia.ai.api.util.HttpRequestUtilities;
import com.sophia.ai.application.security.SecurityUtilities;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.response.ErrorResponse;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.security.ratelimit.RedisRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Two fixed windows per caller: a general one for every endpoint and a tighter one for the endpoints that call the
 * model, which are the expensive ones. The token quotas (see UsageService) are what bound the real spend.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String GENERATION_PREFIX = "/api/v1/ai/";
    private static final String CONVERSATIONS = "/api/v1/ai/conversations";

    private final RedisRateLimiter rateLimiter;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.security().rateLimit().enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String subject = SecurityUtilities.currentUser().map(u -> "user:" + u.id())
                .orElseGet(() -> "ip:" + HttpRequestUtilities.clientIp(request));
        var general = properties.security().rateLimit();

        RedisRateLimiter.Result result = rateLimiter.tryConsume("api:" + subject, general.requests(), general.window());
        response.setHeader("X-RateLimit-Limit", String.valueOf(general.requests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (result.allowed() && isGeneration(request)) {
            var generation = properties.ai().generationRateLimit();
            result = rateLimiter.tryConsume("generate:" + subject, generation.requests(), generation.window());
        }
        if (!result.allowed()) {
            meters.counter("ai.rate_limited").increment();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            ErrorResponse body = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .code(ErrorCode.RATE_LIMITED.name())
                    .message("Too many requests. Please try again in a moment.")
                    .path(request.getRequestURI())
                    .requestId(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY))
                    .build();
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** POSTs under /api/v1/ai/ run the model, except starting a conversation, which only stores a row. */
    private boolean isGeneration(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "POST".equals(request.getMethod()) && uri.startsWith(GENERATION_PREFIX)
                && !uri.equals(CONVERSATIONS) && !uri.equals(CONVERSATIONS + "/");
    }
}
