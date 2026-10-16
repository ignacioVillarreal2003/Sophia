package com.sophia.notification.api.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.notification.api.filter.CorrelationIdFilter;
import com.sophia.notification.api.util.HttpRequestUtilities;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.application.security.SecurityUtilities;
import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.domain.dto.response.ErrorResponse;
import com.sophia.notification.domain.exception.ErrorCode;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import com.sophia.notification.infrastructure.security.ratelimit.RedisRateLimiter;
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
import java.util.Optional;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String BUCKET = "api";

    private final RedisRateLimiter rateLimiter;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;
    private final NotificationMetrics metrics;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.security().rateLimit().enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ApplicationProperties.Security.RateLimit config = properties.security().rateLimit();
        String path = request.getRequestURI();

        RedisRateLimiter.Result result = rateLimiter.tryConsume(subjectOf(request), config.requests(),
                config.window());
        response.setHeader("X-RateLimit-Limit", String.valueOf(config.requests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            metrics.rateLimited(BUCKET);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            ErrorResponse body = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .code(ErrorCode.RATE_LIMITED.name())
                    .message("Too many requests. Please try again later.")
                    .path(path)
                    .requestId(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY))
                    .build();
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String subjectOf(HttpServletRequest request) {
        Optional<AuthenticatedUser> user = SecurityUtilities.currentUser();
        return user.map(u -> "user:" + u.id()).orElseGet(() -> "ip:" + HttpRequestUtilities.clientIp(request));
    }
}
