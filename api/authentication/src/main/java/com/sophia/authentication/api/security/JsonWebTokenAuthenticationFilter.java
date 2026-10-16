package com.sophia.authentication.api.security;

import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.infrastructure.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JsonWebTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_MDC_KEY = "userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JsonWebTokenService jsonWebTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jsonWebTokenService.parseAndValidate(token);
            String type = claims.get(JsonWebTokenService.CLAIM_TYPE, String.class);
            if (!tokenBlacklistService.isBlacklisted(claims.getId())) {
                CustomUserDetails principal = switch (type == null ? "" : type) {
                    case JsonWebTokenService.TYPE_ACCESS -> userPrincipal(claims);
                    case JsonWebTokenService.TYPE_SERVICE -> servicePrincipal(claims);
                    default -> null;
                };
                if (principal != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    MDC.put(USER_ID_MDC_KEY, claims.getSubject());
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected access token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }

    private CustomUserDetails userPrincipal(Claims claims) {
        return CustomUserDetails.fromJsonWebToken(
                UUID.fromString(claims.getSubject()),
                claims.get(JsonWebTokenService.CLAIM_EMAIL, String.class),
                UUID.fromString(claims.get(JsonWebTokenService.CLAIM_SESSION_ID, String.class)),
                userAuthorities(claims));
    }

    private CustomUserDetails servicePrincipal(Claims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SERVICE"));
        if (claims.get(JsonWebTokenService.CLAIM_SCOPES) instanceof List<?> scopes) {
            scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority(String.valueOf(scope))));
        }
        return CustomUserDetails.fromJsonWebToken(
                UUID.fromString(claims.getSubject()),
                "service:" + claims.get(JsonWebTokenService.CLAIM_CLIENT_NAME, String.class),
                null,
                authorities);
    }

    private List<GrantedAuthority> userAuthorities(Claims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (claims.get(JsonWebTokenService.CLAIM_ROLES) instanceof List<?> roleList) {
            roleList.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        if (claims.get(JsonWebTokenService.CLAIM_PERMISSIONS) instanceof List<?> permissionList) {
            permissionList.forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority(String.valueOf(permission))));
        }
        return authorities;
    }
}
