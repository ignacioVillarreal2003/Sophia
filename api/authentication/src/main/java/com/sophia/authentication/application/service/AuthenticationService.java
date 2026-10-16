package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.dto.request.DeleteAccountRequest;
import com.sophia.authentication.domain.dto.request.LoginRequest;
import com.sophia.authentication.domain.dto.request.RegisterRequest;
import com.sophia.authentication.domain.dto.response.AuthenticationResponse;
import com.sophia.authentication.domain.dto.response.UserResponse;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import com.sophia.authentication.domain.exception.AccountDisabledException;
import com.sophia.authentication.domain.exception.AccountLockedException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.EmailNotVerifiedException;
import com.sophia.authentication.domain.exception.InvalidCredentialsException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.application.mapper.UserMapper;
import com.sophia.authentication.application.security.SecurityUtilities;
import com.sophia.authentication.infrastructure.repository.OAuthAccountRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserPreferencesRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.infrastructure.messaging.UserEventPublisher;
import com.sophia.authentication.infrastructure.messaging.UserEventType;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.service.TokenBlacklistService;
import com.sophia.authentication.domain.util.AuditActions;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AuthenticationService {

    public static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JsonWebTokenService jsonWebTokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final ApplicationProperties properties;
    private final AuthenticationMetrics authenticationMetrics;
    private final UserEventPublisher userEventPublisher;

    private final String timingEqualizationHash;

    public AuthenticationService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserPreferencesRepository userPreferencesRepository,
                       OAuthAccountRepository oauthAccountRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       JsonWebTokenService jsonWebTokenService,
                       LoginAttemptService loginAttemptService,
                       EmailVerificationService emailVerificationService,
                       TokenBlacklistService tokenBlacklistService,
                       AuditService auditService,
                       UserMapper userMapper,
                       ApplicationProperties properties,
                       AuthenticationMetrics authenticationMetrics,
                       UserEventPublisher userEventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jsonWebTokenService = jsonWebTokenService;
        this.loginAttemptService = loginAttemptService;
        this.emailVerificationService = emailVerificationService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.auditService = auditService;
        this.userMapper = userMapper;
        this.properties = properties;
        this.userEventPublisher = userEventPublisher;
        this.authenticationMetrics = authenticationMetrics;
        this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = User.create(
                request.firstName() == null ? "" : request.firstName().trim(),
                request.lastName() == null ? "" : request.lastName().trim(),
                request.email().toLowerCase().trim(),
                passwordEncoder.encode(request.password()),
                AuthenticationProvider.LOCAL, true, false,
                new HashSet<>(Set.of(roleRepository.findByName(DEFAULT_ROLE)
                        .orElseThrow(() -> new IllegalStateException("Default role USER is not seeded")))));
        user = userRepository.save(user);
        user.assignPreferences(userPreferencesRepository.save(UserPreferences.create(user, null)));

        emailVerificationService.sendVerificationToken(user);
        auditService.success(AuditActions.REGISTER, user.getId(), user.getEmail());
        authenticationMetrics.userRegistered(AuthenticationMetrics.METHOD_PASSWORD);
        userEventPublisher.publish(UserEventType.USER_CREATED, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public AuthenticationResponse login(LoginRequest request, String ip, String userAgent) {
        loginAttemptService.assertIpNotBlocked(ip);

        User user = userRepository.findByEmailWithAuthorities(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), timingEqualizationHash);
            recordFailedLogin(null, request.email(), ip, userAgent, "USER_NOT_FOUND");
            throw new InvalidCredentialsException();
        }

        if (user.isLocked()) {
            recordFailedLogin(user, request.email(), ip, userAgent, "ACCOUNT_LOCKED");
            throw new AccountLockedException("Account is temporarily locked. Try again later.");
        }

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            boolean lockedNow = user.getPassword() != null && loginAttemptService.onFailedLogin(user);
            recordFailedLogin(user, request.email(), ip, userAgent, "BAD_CREDENTIALS");
            if (lockedNow) {
                auditService.record(user.getId(), user.getEmail(), AuditActions.ACCOUNT_LOCKED, null, null,
                        AuditLog.Result.FAILURE, Map.of("ip", String.valueOf(ip)));
                throw new AccountLockedException("Account locked after too many failed attempts");
            }
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            recordFailedLogin(user, request.email(), ip, userAgent, "ACCOUNT_DISABLED");
            throw new AccountDisabledException();
        }

        if (properties.security().requireEmailVerification()
                && user.getAuthenticationProvider() == AuthenticationProvider.LOCAL
                && !user.isEmailVerified()) {
            recordFailedLogin(user, request.email(), ip, userAgent, "EMAIL_NOT_VERIFIED");
            throw new EmailNotVerifiedException();
        }

        loginAttemptService.onSuccessfulLogin(user);
        loginAttemptService.record(request.email(), ip, userAgent, true, null);

        UserSession session = tokenService.createSession(user, ip, userAgent, request.deviceName(), request.rememberMe());
        TokenService.IssuedTokens tokens = tokenService.issue(user, session, ip, userAgent);

        auditService.success(AuditActions.LOGIN, user.getId(), user.getEmail());
        authenticationMetrics.loginSucceeded(AuthenticationMetrics.METHOD_PASSWORD);
        return AuthenticationResponse.of(tokens.accessToken(), tokens.rawRefreshToken(), tokens.expiresInSeconds(),
                userMapper.toResponse(user));
    }

    @Transactional
    public AuthenticationResponse refresh(String rawRefreshToken, String ip, String userAgent) {
        TokenService.RotationResult result = tokenService.rotate(rawRefreshToken, ip, userAgent);
        return AuthenticationResponse.of(result.tokens().accessToken(), result.tokens().rawRefreshToken(),
                result.tokens().expiresInSeconds(), userMapper.toResponse(result.user()));
    }

    @Transactional
    public void logout(String bearerToken, String rawRefreshToken) {
        blacklistAccessToken(bearerToken);
        if (StringUtils.hasText(rawRefreshToken)) {
            tokenService.revokeByRawToken(rawRefreshToken);
        }
        auditService.success(AuditActions.LOGOUT, null, null);
    }

    @Transactional
    public void logoutAll(UUID userId, String bearerToken) {
        tokenService.revokeAllForUser(userId);
        blacklistAccessToken(bearerToken);
        auditService.success(AuditActions.LOGOUT_ALL, userId, null);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userRepository.findByIdWithAuthorities(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request, String bearerToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getPassword() != null
                && (request.password() == null || !passwordEncoder.matches(request.password(), user.getPassword()))) {
            throw new InvalidCredentialsException();
        }

        tokenService.revokeAllForUser(userId);
        blacklistAccessToken(bearerToken);
        String email = user.getEmail();
        oauthAccountRepository.deleteByUserId(userId);
        user.softDelete(SecurityUtilities.getCurrentUserEmail().orElse(email));
        userRepository.save(user);
        auditService.record(userId, email, AuditActions.ACCOUNT_DELETED, "User", userId.toString(),
                AuditLog.Result.SUCCESS, null);
        userEventPublisher.publish(UserEventType.USER_DELETED, userId, email);
    }

    private void recordFailedLogin(User user, String email, String ip, String userAgent, String reason) {
        loginAttemptService.record(email, ip, userAgent, false, reason);
        auditService.record(user != null ? user.getId() : null, email, AuditActions.LOGIN_FAILED, null, null,
                AuditLog.Result.FAILURE, Map.of("reason", reason, "ip", String.valueOf(ip)));
        authenticationMetrics.loginFailed(AuthenticationMetrics.METHOD_PASSWORD, reason);
    }

    private void blacklistAccessToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            return;
        }
        try {
            Claims claims = jsonWebTokenService.parseAndValidate(bearerToken.substring(7));
            tokenBlacklistService.blacklist(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Skipping blacklist of unparseable access token: {}", e.getMessage());
        }
    }
}
