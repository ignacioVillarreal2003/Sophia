package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.RefreshToken;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.exception.AccountDisabledException;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.repository.RefreshTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.domain.util.AuditActions;
import com.sophia.authentication.domain.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final JsonWebTokenService jsonWebTokenService;
    private final AuditService auditService;
    private final SessionRevocationService sessionRevocationService;
    private final ApplicationProperties properties;
    private final AuthenticationMetrics authenticationMetrics;

    public record IssuedTokens(String accessToken, String rawRefreshToken, long expiresInSeconds) {
    }

    public record RotationResult(User user, UserSession session, IssuedTokens tokens) {
    }

    @Transactional
    public UserSession createSession(User user, String ip, String userAgent, String deviceName, boolean rememberMe) {
        Duration ttl = rememberMe
                ? properties.security().jwt().refreshTokenRememberMeTtl()
                : properties.security().jwt().refreshTokenTtl();
        UserSession session = UserSession.create(
                user, ip, userAgent, deviceName, rememberMe, Instant.now(), Instant.now().plus(ttl));
        return userSessionRepository.save(session);
    }

    @Transactional
    public IssuedTokens issue(User user, UserSession session, String ip, String userAgent) {
        String accessToken = jsonWebTokenService.generateAccessToken(user, session.getId());
        String rawRefreshToken = TokenHasher.generateToken();

        Duration ttl = session.isRememberMe()
                ? properties.security().jwt().refreshTokenRememberMeTtl()
                : properties.security().jwt().refreshTokenTtl();
        Instant expiresAt = Instant.now().plus(ttl);
        if (expiresAt.isAfter(session.getExpiresAt())) {
            expiresAt = session.getExpiresAt();
        }

        refreshTokenRepository.save(RefreshToken.create(
                TokenHasher.sha256Hex(rawRefreshToken), user, session, expiresAt, ip, userAgent));

        authenticationMetrics.tokenIssued("access");
        authenticationMetrics.tokenIssued("refresh");
        return new IssuedTokens(accessToken, rawRefreshToken, jsonWebTokenService.getAccessTokenTtlSeconds());
    }

    @Transactional
    public RotationResult rotate(String rawRefreshToken, String ip, String userAgent) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (current.isRevoked()) {
            UUID sessionId = current.getSession().getId();
            log.warn("Refresh token reuse detected for user={} session={}", current.getUser().getId(), sessionId);
            sessionRevocationService.revokeSessionFamily(sessionId);
            auditService.record(current.getUser().getId(), null, AuditActions.TOKEN_REUSE_DETECTED, "UserSession",
                    sessionId.toString(), AuditLog.Result.FAILURE, Map.of("ip", String.valueOf(ip)));
            throw new InvalidTokenException("Refresh token reuse detected; session has been revoked");
        }
        if (current.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        UserSession session = current.getSession();
        if (!session.isActive()) {
            throw new InvalidTokenException("Session is no longer active");
        }

        User user = userRepository.findByIdWithAuthorities(current.getUser().getId())
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        IssuedTokens tokens = issue(user, session, ip, userAgent);
        current.revoke(TokenHasher.sha256Hex(tokens.rawRefreshToken()));
        session.touch(ip);

        return new RotationResult(user, session, tokens);
    }

    @Transactional
    public void revokeByRawToken(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .ifPresent(token -> revokeSessionInternal(token.getSession()));
    }

    @Transactional
    public void revokeSession(UUID sessionId) {
        userSessionRepository.findById(sessionId).ifPresent(this::revokeSessionInternal);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllByUserId(userId, now);
        userSessionRepository.revokeAllByUserId(userId, now);
    }

    @Transactional
    public void revokeAllForUserExcept(UUID userId, UUID sessionIdToKeep) {
        Instant now = Instant.now();
        userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastActiveAtDesc(userId, now)
                .stream()
                .filter(session -> !session.getId().equals(sessionIdToKeep))
                .forEach(this::revokeSessionInternal);
    }

    private void revokeSessionInternal(UserSession session) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllBySessionId(session.getId(), now);
        if (session.getRevokedAt() == null) {
            session.revoke();
            userSessionRepository.save(session);
        }
    }
}
