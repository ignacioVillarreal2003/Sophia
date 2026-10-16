package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.dto.request.LoginRequest;
import com.sophia.authentication.domain.dto.request.RegisterRequest;
import com.sophia.authentication.domain.dto.response.AuthenticationResponse;
import com.sophia.authentication.domain.dto.response.UserResponse;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.exception.AccountDisabledException;
import com.sophia.authentication.domain.exception.AccountLockedException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.EmailNotVerifiedException;
import com.sophia.authentication.domain.exception.InvalidCredentialsException;
import com.sophia.authentication.application.mapper.UserMapper;
import com.sophia.authentication.infrastructure.messaging.UserEventPublisher;
import com.sophia.authentication.infrastructure.repository.OAuthAccountRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserPreferencesRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.application.security.JsonWebTokenKeyManager;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.application.service.AuthenticationService;
import com.sophia.authentication.application.service.EmailVerificationService;
import com.sophia.authentication.application.service.LoginAttemptService;
import com.sophia.authentication.application.service.TokenService;
import com.sophia.authentication.infrastructure.service.TokenBlacklistService;
import com.sophia.authentication.support.TestFixtures;
import com.sophia.authentication.support.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String UA = "JUnit";
    private static final String RAW_PASSWORD = "S3cure!Password";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserPreferencesRepository userPreferencesRepository;
    @Mock private OAuthAccountRepository oauthAccountRepository;
    @Mock private TokenService tokenService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private AuditService auditService;
    @Mock private UserEventPublisher userEventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    private AuthenticationService authenticationService;
    private User user;

    @BeforeEach
    void setUp() {
        JsonWebTokenService jsonWebTokenService = new JsonWebTokenService(TestFixtures.appProperties(),
                new JsonWebTokenKeyManager(TestFixtures.appProperties()));
        authenticationService = new AuthenticationService(userRepository, roleRepository, userPreferencesRepository,
                oauthAccountRepository, passwordEncoder, tokenService, jsonWebTokenService, loginAttemptService,
                emailVerificationService, tokenBlacklistService, auditService, userMapper,
                TestFixtures.appProperties(), TestFixtures.authenticationMetrics(), userEventPublisher);
        user = TestFixtures.user("jane@test.local", passwordEncoder.encode(RAW_PASSWORD));
    }

    private void stubTokenIssuance() {
        UserSession session = TestIds.withRandomId(
                UserSession.create(user, null, null, null, false, null, Instant.now().plusSeconds(3600)));
        when(tokenService.createSession(any(), any(), any(), any(), anyBoolean())).thenReturn(session);
        when(tokenService.issue(any(), any(), any(), any()))
                .thenReturn(new TokenService.IssuedTokens("access-token", "refresh-token", 900));
    }

    @Test
    @DisplayName("register rejects duplicate emails")
    void registerDuplicate() {
        when(userRepository.existsByEmailIgnoreCase("jane@test.local")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(
                new RegisterRequest("Jane", "Doe", "jane@test.local", RAW_PASSWORD)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register stores the user with the default role and sends a verification email")
    void registerSuccess() {
        when(userRepository.existsByEmailIgnoreCase("Jane@Test.Local")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(TestFixtures.role("USER")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
                TestIds.withRandomId(invocation.getArgument(0)));
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authenticationService.register(
                new RegisterRequest("Jane", "Doe", "Jane@Test.Local", RAW_PASSWORD));

        assertThat(response.email()).isEqualTo("jane@test.local");
        assertThat(response.emailVerified()).isFalse();
        verify(emailVerificationService).sendVerificationToken(any(User.class));
    }

    @Test
    @DisplayName("login returns a token pair on valid credentials")
    void loginSuccess() {
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));
        stubTokenIssuance();

        AuthenticationResponse response = authenticationService.login(
                new LoginRequest("jane@test.local", RAW_PASSWORD, false, null), IP, UA);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(loginAttemptService).onSuccessfulLogin(user);
        verify(loginAttemptService).record(eq("jane@test.local"), eq(IP), eq(UA), eq(true), any());
    }

    @Test
    @DisplayName("login fails with generic error for unknown accounts")
    void loginUnknownUser() {
        when(userRepository.findByEmailWithAuthorities(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("ghost@test.local", RAW_PASSWORD, false, null), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).record(eq("ghost@test.local"), eq(IP), eq(UA), eq(false), eq("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("login fails with generic error on wrong password")
    void loginWrongPassword() {
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));
        when(loginAttemptService.onFailedLogin(user)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("jane@test.local", "Wrong!Pass1", false, null), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login reports a lock when the failure threshold is crossed")
    void loginLocksAccount() {
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));
        when(loginAttemptService.onFailedLogin(user)).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("jane@test.local", "Wrong!Pass1", false, null), IP, UA))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("login rejects locked accounts without checking the password")
    void loginRejectsLocked() {
        user.lock(Instant.now().plusSeconds(600));
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("jane@test.local", RAW_PASSWORD, false, null), IP, UA))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("login rejects disabled accounts")
    void loginRejectsDisabled() {
        user.updateActiveStatus(false);
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("jane@test.local", RAW_PASSWORD, false, null), IP, UA))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    @DisplayName("login rejects unverified local accounts when verification is required")
    void loginRejectsUnverified() {
        user.unverifyEmail();
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("jane@test.local", RAW_PASSWORD, false, null), IP, UA))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    @DisplayName("logout revokes the refresh token session")
    void logoutRevokesRefreshToken() {
        lenient().when(userRepository.findByEmailWithAuthorities(anyString())).thenReturn(Optional.empty());

        authenticationService.logout(null, "some-refresh-token");

        verify(tokenService).revokeByRawToken("some-refresh-token");
    }
}
