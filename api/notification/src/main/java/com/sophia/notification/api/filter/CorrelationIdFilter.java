package com.sophia.notification.api.filter;

import com.sophia.notification.api.util.HttpRequestUtilities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ensures every request carries a correlation id: propagated from the caller when
 * present, generated otherwise. The id is exposed in the MDC (structured logs),
 * the response header, and error payloads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String CLIENT_IP_MDC_KEY = "clientIp";
    public static final String USER_AGENT_MDC_KEY = "userAgent";

    private static final Pattern ACCEPTABLE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = StringUtils.hasText(incoming) && ACCEPTABLE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        String clientIp = HttpRequestUtilities.clientIp(request);
        if (clientIp != null) {
            MDC.put(CLIENT_IP_MDC_KEY, clientIp);
        }
        String userAgent = HttpRequestUtilities.userAgent(request);
        if (userAgent != null) {
            MDC.put(USER_AGENT_MDC_KEY, userAgent);
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
            MDC.remove(CLIENT_IP_MDC_KEY);
            MDC.remove(USER_AGENT_MDC_KEY);
        }
    }
}
