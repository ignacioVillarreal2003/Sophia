package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.application.mapper.UserMapper;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.application.service.GoogleAuthenticationService;
import com.sophia.authentication.application.service.LoginAttemptService;
import com.sophia.authentication.application.service.TokenService;
import com.sophia.authentication.domain.dto.request.GoogleLoginRequest;
import com.sophia.authentication.domain.dto.response.AuthenticationResponse;
import com.sophia.authentication.domain.entity.OAuthAccount;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import com.sophia.authentication.domain.exception.AccountDisabledException;
import com.sophia.authentication.domain.exception.AccountLockedException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.domain.util.AuditActions;
import com.sophia.authentication.infrastructure.messaging.UserEventPublisher;
import com.sophia.authentication.infrastructure.repository.OAuthAccountRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserPreferencesRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.security.oauth2.GoogleTokenVerifier;
import com.sophia.authentication.infrastructure.security.oauth2.GoogleUserInfo;
import com.sophia.authentication.infrastructure.service.OneTimeCodeService;
import com.sophia.authentication.support.TestFixtures;
import com.sophia.authentication.support.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthenticationServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String UA = "JUnit";
    private static final String ID_TOKEN = "google-id-token";

    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserPreferencesRepository userPreferencesRepository;
    @Mock private OAuthAccountRepository oauthAccountRepository;
    @Mock private TokenService tokenService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuditService auditService;
    @Mock private OneTimeCodeService oneTimeCodeService;
    @Mock private UserEventPublisher userEventPublisher;

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    private GoogleAuthenticationService googleAuthenticationService;
    private User user;
    private GoogleUserInfo info;

    @BeforeEach
    void setUp() {
        googleAuthenticationService = new GoogleAuthenticationService(googleTokenVerifier, userRepository,
                roleRepository, userPreferencesRepository, oauthAccountRepository, tokenService, loginAttemptService,
                auditService, userMapper, oneTimeCodeService, TestFixtures.authenticationMetrics(), userEventPublisher);
        user = TestFixtures.user("jane@test.local", null);
        info = new GoogleUserInfo("google-subject-1", "jane@test.local", true, "Jane", "Doe",
                "https://img.test/jane.png");
    }

    private OAuthAccount linkedAccount() {
        return OAuthAccount.create(user, AuthenticationProvider.GOOGLE, info.subject(), info.email(), null);
    }

    private void stubTokenIssuance() {
        UserSession session = TestIds.withRandomId(
                UserSession.create(user, null, null, null, false, null, Instant.now().plusSeconds(3600)));
        when(tokenService.createSession(any(), any(), any(), any(), anyBoolean())).thenReturn(session);
        when(tokenService.issue(any(), any(), any(), any()))
                .thenReturn(new TokenService.IssuedTokens("access-token", "refresh-token", 900));
    }

    @Test
    @DisplayName("login issues tokens for an already linked Google account")
    void loginLinkedAccount() {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(info);
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.of(linkedAccount()));
        when(userRepository.findByIdWithAuthorities(user.getId())).thenReturn(Optional.of(user));
        stubTokenIssuance();

        AuthenticationResponse response = googleAuthenticationService.login(
                new GoogleLoginRequest(ID_TOKEN, false, null), IP, UA);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(loginAttemptService).onSuccessfulLogin(user);
        verify(loginAttemptService).record(eq("jane@test.local"), eq(IP), eq(UA), eq(true), any());
        verify(auditService).success(AuditActions.LOGIN_GOOGLE, user.getId(), user.getEmail());
    }

    @Test
    @DisplayName("login rejects Google accounts whose email is not verified")
    void loginRejectsUnverifiedGoogleEmail() {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleUserInfo(
                "google-subject-1", "jane@test.local", false, "Jane", "Doe", null));

        assertThatThrownBy(() -> googleAuthenticationService.login(
                new GoogleLoginRequest(ID_TOKEN, false, null), IP, UA))
                .isInstanceOf(InvalidTokenException.class);
        verify(tokenService, never()).issue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("login rejects locked accounts and records the failed attempt")
    void loginRejectsLocked() {
        user.lock(Instant.now().plusSeconds(600));
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(info);
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.of(linkedAccount()));

        assertThatThrownBy(() -> googleAuthenticationService.login(
                new GoogleLoginRequest(ID_TOKEN, false, null), IP, UA))
                .isInstanceOf(AccountLockedException.class);
        verify(loginAttemptService).record(eq("jane@test.local"), eq(IP), eq(UA), eq(false), eq("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("login rejects disabled accounts and records the failed attempt")
    void loginRejectsDisabled() {
        user.updateActiveStatus(false);
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(info);
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.of(linkedAccount()));

        assertThatThrownBy(() -> googleAuthenticationService.login(
                new GoogleLoginRequest(ID_TOKEN, false, null), IP, UA))
                .isInstanceOf(AccountDisabledException.class);
        verify(loginAttemptService).record(eq("jane@test.local"), eq(IP), eq(UA), eq(false), eq("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("provisioning links the Google identity to an existing user with the same email")
    void provisionLinksExistingUser() {
        user.getPreferences().updatePhoto(null);
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(info.email())).thenReturn(Optional.of(user));

        User provisioned = googleAuthenticationService.provisionUser(info);

        assertThat(provisioned).isSameAs(user);
        assertThat(provisioned.getPreferences().getPhotoUrl()).isEqualTo(info.pictureUrl());
        ArgumentCaptor<OAuthAccount> captor = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getProviderUserId()).isEqualTo(info.subject());
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("provisioning registers a brand new Google user with the default role")
    void provisionRegistersNewUser() {
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(info.email())).thenReturn(Optional.empty());
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(TestFixtures.role("USER")));
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
                TestIds.withRandomId(invocation.getArgument(0)));
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User provisioned = googleAuthenticationService.provisionUser(info);

        assertThat(provisioned.getEmail()).isEqualTo("jane@test.local");
        assertThat(provisioned.getAuthenticationProvider()).isEqualTo(AuthenticationProvider.GOOGLE);
        assertThat(provisioned.isEmailVerified()).isTrue();
        assertThat(provisioned.getPassword()).isNull();
        verify(auditService).success(AuditActions.REGISTER, provisioned.getId(), provisioned.getEmail());
        verify(oauthAccountRepository).save(any(OAuthAccount.class));
    }

    @Test
    @DisplayName("provisioning refreshes provider data for an already linked account")
    void provisionRefreshesProviderData() {
        User googleUser = TestIds.withRandomId(User.create(
                "Jane", "Doe", "jane@test.local", null, AuthenticationProvider.GOOGLE, true, true,
                Set.of(TestFixtures.role("USER"))));
        googleUser.assignPreferences(UserPreferences.create(googleUser, null));
        OAuthAccount account = OAuthAccount.create(
                googleUser, AuthenticationProvider.GOOGLE, info.subject(), info.email(), null);
        GoogleUserInfo updated = new GoogleUserInfo("google-subject-1", "jane@test.local", true,
                "Janet", "Doe", "https://img.test/new.png");
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.of(account));

        User provisioned = googleAuthenticationService.provisionUser(updated);

        assertThat(provisioned).isSameAs(googleUser);
        assertThat(account.getName()).isEqualTo("Janet Doe");
        assertThat(googleUser.getPreferences().getPhotoUrl()).isEqualTo("https://img.test/new.png");
    }

    @Test
    @DisplayName("exchanging a one-time code issues tokens for the code owner")
    void exchangeOneTimeCode() {
        when(oneTimeCodeService.consume("code-1")).thenReturn(user.getId());
        when(userRepository.findByIdWithAuthorities(user.getId())).thenReturn(Optional.of(user));
        stubTokenIssuance();

        AuthenticationResponse response = googleAuthenticationService.exchangeOneTimeCode("code-1", IP, UA);

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(auditService).success(AuditActions.LOGIN_GOOGLE, user.getId(), user.getEmail());
    }

    @Test
    @DisplayName("exchanging a code for a deleted user fails")
    void exchangeOneTimeCodeUnknownUser() {
        UUID ghost = UUID.randomUUID();
        when(oneTimeCodeService.consume("code-1")).thenReturn(ghost);
        when(userRepository.findByIdWithAuthorities(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleAuthenticationService.exchangeOneTimeCode("code-1", IP, UA))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("linking fails when the user already has a Google account")
    void linkRejectsSecondGoogleAccount() {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(info);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(oauthAccountRepository.existsByUserIdAndProvider(user.getId(), AuthenticationProvider.GOOGLE))
                .thenReturn(true);

        assertThatThrownBy(() -> googleAuthenticationService.link(user.getId(), ID_TOKEN))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("linking fails when the Google identity belongs to another user")
    void linkRejectsForeignGoogleAccount() {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(info);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(oauthAccountRepository.existsByUserIdAndProvider(user.getId(), AuthenticationProvider.GOOGLE))
                .thenReturn(false);
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.of(linkedAccount()));

        assertThatThrownBy(() -> googleAuthenticationService.link(user.getId(), ID_TOKEN))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("linking stores the Google identity and audits it")
    void linkSuccess() {
        when(googleTokenVerifier.verify(ID_TOKEN)).thenReturn(info);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(oauthAccountRepository.existsByUserIdAndProvider(user.getId(), AuthenticationProvider.GOOGLE))
                .thenReturn(false);
        when(oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject()))
                .thenReturn(Optional.empty());

        googleAuthenticationService.link(user.getId(), ID_TOKEN);

        verify(oauthAccountRepository).save(any(OAuthAccount.class));
        verify(auditService).success(AuditActions.GOOGLE_LINKED, user.getId(), user.getEmail());
    }

    @Test
    @DisplayName("unlinking is blocked while the account has no password")
    void unlinkRequiresPassword() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(oauthAccountRepository.findByUserIdAndProvider(user.getId(), AuthenticationProvider.GOOGLE))
                .thenReturn(Optional.of(linkedAccount()));

        assertThatThrownBy(() -> googleAuthenticationService.unlink(user.getId()))
                .isInstanceOf(OperationNotAllowedException.class);
        verify(oauthAccountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("unlinking removes the Google identity when a password exists")
    void unlinkSuccess() {
        user.changePassword("encoded-password");
        OAuthAccount account = linkedAccount();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(oauthAccountRepository.findByUserIdAndProvider(user.getId(), AuthenticationProvider.GOOGLE))
                .thenReturn(Optional.of(account));

        googleAuthenticationService.unlink(user.getId());

        verify(oauthAccountRepository).delete(account);
        verify(auditService).record(null, null, AuditActions.GOOGLE_UNLINKED, "User",
                user.getId().toString(), AuditLog.Result.SUCCESS, null);
    }
}
