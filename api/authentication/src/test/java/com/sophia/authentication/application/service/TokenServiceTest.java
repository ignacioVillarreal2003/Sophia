package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.RefreshToken;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.infrastructure.repository.RefreshTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.application.service.SessionRevocationService;
import com.sophia.authentication.application.service.TokenService;
import com.sophia.authentication.support.TestFixtures;
import com.sophia.authentication.support.TestIds;
import com.sophia.authentication.domain.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private JsonWebTokenService jsonWebTokenService;
    @Mock private AuditService auditService;
    @Mock private SessionRevocationService sessionRevocationService;

    private TokenService tokenService;
    private User user;
    private UserSession session;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(refreshTokenRepository, userSessionRepository, userRepository,
                jsonWebTokenService, auditService, sessionRevocationService, TestFixtures.appProperties(),
                TestFixtures.authenticationMetrics());
        user = TestFixtures.user("jane@test.local", "hash");
        session = TestIds.withRandomId(
                UserSession.create(user, null, null, null, false, null, Instant.now().plusSeconds(86400)));
    }

    @Test
    @DisplayName("rotation revokes the old token and links it to the new one")
    void rotateHappyPath() {
        String raw = TokenHasher.generateToken();
        RefreshToken current = RefreshToken.create(
                TokenHasher.sha256Hex(raw), user, session, Instant.now().plusSeconds(3600), null, null);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(raw)))
                .thenReturn(Optional.of(current));
        when(userRepository.findByIdWithAuthorities(user.getId())).thenReturn(Optional.of(user));
        when(jsonWebTokenService.generateAccessToken(eq(user), eq(session.getId()))).thenReturn("new-access");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenService.RotationResult result = tokenService.rotate(raw, "10.0.0.1", "JUnit");

        assertThat(result.tokens().accessToken()).isEqualTo("new-access");
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedByTokenHash())
                .isEqualTo(TokenHasher.sha256Hex(result.tokens().rawRefreshToken()));
    }

    @Test
    @DisplayName("presenting a rotated token revokes the whole session family")
    void rotateDetectsReuse() {
        String raw = TokenHasher.generateToken();
        RefreshToken revoked = RefreshToken.create(
                TokenHasher.sha256Hex(raw), user, session, Instant.now().plusSeconds(3600), null, null);
        revoked.revoke(null);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(raw)))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> tokenService.rotate(raw, "10.0.0.1", "JUnit"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("reuse");
        verify(sessionRevocationService).revokeSessionFamily(session.getId());
    }

    @Test
    @DisplayName("expired refresh tokens are rejected")
    void rotateRejectsExpired() {
        String raw = TokenHasher.generateToken();
        RefreshToken expired = RefreshToken.create(
                TokenHasher.sha256Hex(raw), user, session, Instant.now().minusSeconds(10), null, null);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(raw)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> tokenService.rotate(raw, "10.0.0.1", "JUnit"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("unknown refresh tokens are rejected")
    void rotateRejectsUnknown() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.rotate("garbage", "10.0.0.1", "JUnit"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("the issued refresh token never outlives its session")
    void issueCapsExpiryAtSessionEnd() {
        UserSession shortSession = TestIds.withRandomId(
                UserSession.create(user, null, null, null, false, null, Instant.now().plusSeconds(60)));
        when(jsonWebTokenService.generateAccessToken(any(), any())).thenReturn("access");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            assertThat(token.getExpiresAt()).isBeforeOrEqualTo(shortSession.getExpiresAt());
            return token;
        });

        tokenService.issue(user, shortSession, "10.0.0.1", "JUnit");
    }
}
