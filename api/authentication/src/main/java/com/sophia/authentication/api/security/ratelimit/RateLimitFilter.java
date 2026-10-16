package com.sophia.authentication.api.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.api.filter.CorrelationIdFilter;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.dto.response.ErrorResponse;
import com.sophia.authentication.domain.exception.ErrorCode;
import com.sophia.authentication.api.util.HttpRequestUtilities;
import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.application.security.SecurityUtilities;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.security.ratelimit.RedisRateLimiter;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthenticationMetrics authenticationMetrics;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.security().rateLimit().enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs") || path.startsWith("/files")
                || path.startsWith("/.well-known");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ApplicationProperties.Security.RateLimit config = properties.security().rateLimit();
        String path = request.getRequestURI();

        boolean isAuthenticationEndpoint = path.startsWith("/api/v1/authentication");
        int limit = isAuthenticationEndpoint ? config.authenticationRequests() : config.generalRequests();
        Duration window = isAuthenticationEndpoint ? config.authenticationWindow() : config.generalWindow();
        String bucket = isAuthenticationEndpoint ? "authentication:" : "general:";

        RedisRateLimiter.Result result = rateLimiter.tryConsume(bucket + subjectOf(request), limit, window);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            authenticationMetrics.rateLimitRejected(isAuthenticationEndpoint ? "authentication" : "general");
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
        Optional<CustomUserDetails> user = SecurityUtilities.currentUser();
        return user.map(u -> "user:" + u.getId()).orElseGet(() -> "ip:" + HttpRequestUtilities.clientIp(request));
    }
}
