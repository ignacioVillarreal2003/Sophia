package com.sophia.authentication.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.domain.dto.response.ErrorResponse;
import com.sophia.authentication.domain.exception.ErrorCode;
import com.sophia.authentication.domain.util.TokenHasher;
import com.sophia.authentication.infrastructure.service.IdempotencyStore;
import com.sophia.authentication.api.util.HttpRequestUtilities;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAY_HEADER = "Idempotency-Replayed";

    private static final int MAX_CACHED_BODY_BYTES = 512 * 1024;

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !StringUtils.hasText(request.getHeader(HEADER))
                || !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = buildKey(request);

        IdempotencyStore.CachedResponse cached = idempotencyStore.find(key).orElse(null);
        if (cached != null) {
            replay(response, cached);
            return;
        }

        if (!idempotencyStore.tryLock(key)) {
            reject(request, response);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
            if (wrapper.getStatus() >= 200 && wrapper.getStatus() < 300
                    && wrapper.getContentSize() <= MAX_CACHED_BODY_BYTES) {
                idempotencyStore.save(key, new IdempotencyStore.CachedResponse(
                        wrapper.getStatus(), wrapper.getContentType(), wrapper.getContentAsByteArray()));
            }
        } finally {
            idempotencyStore.unlock(key);
            wrapper.copyBodyToResponse();
        }
    }

    private String buildKey(HttpServletRequest request) {
        String caller = request.getHeader("Authorization");
        if (!StringUtils.hasText(caller)) {
            caller = HttpRequestUtilities.clientIp(request);
        }
        return TokenHasher.sha256Hex(request.getHeader(HEADER) + '|' + request.getMethod()
                + '|' + request.getRequestURI() + '|' + caller);
    }

    private void replay(HttpServletResponse response, IdempotencyStore.CachedResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        response.setHeader(REPLAY_HEADER, "true");
        response.getOutputStream().write(cached.body());
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .code(ErrorCode.CONFLICT.name())
                .message("A request with the same Idempotency-Key is already in progress")
                .path(request.getRequestURI())
                .requestId(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY))
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
