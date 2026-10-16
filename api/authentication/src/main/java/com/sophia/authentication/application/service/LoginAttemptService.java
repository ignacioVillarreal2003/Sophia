package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.LoginAttempt;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.exception.TooManyRequestsException;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.repository.LoginAttemptRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.security.LoginFailureBudget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private static final int IP_BUDGET_MULTIPLIER = 5;

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
    private final LoginFailureBudget loginFailureBudget;
    private final ApplicationProperties properties;
    private final AuthenticationMetrics authenticationMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String email, String ip, String userAgent, boolean successful, String failureReason) {
        loginAttemptRepository.save(LoginAttempt.create(
                email.toLowerCase(), ip, userAgent, successful, failureReason));
        if (!successful) {
            loginFailureBudget.recordFailure(ip, properties.security().lockout().attemptWindow());
        }
    }

    public void assertIpNotBlocked(String ip) {
        ApplicationProperties.Security.Lockout lockout = properties.security().lockout();
        long failures = loginFailureBudget.failureCount(ip);
        if (failures >= (long) lockout.maxAttempts() * IP_BUDGET_MULTIPLIER) {
            log.warn("IP {} blocked after {} failed login attempts", ip, failures);
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean onFailedLogin(User user) {
        ApplicationProperties.Security.Lockout lockout = properties.security().lockout();
        user.recordFailedLoginAttempt();
        if (user.getFailedLoginAttempts() >= lockout.maxAttempts()) {
            user.lock(Instant.now().plus(lockout.lockDuration()));
            userRepository.lockAccount(user.getId(), user.getLockedUntil());
            log.warn("Account {} locked until {}", user.getEmail(), user.getLockedUntil());
            authenticationMetrics.accountLocked();
            return true;
        }
        userRepository.updateFailedLoginAttempts(user.getId(), user.getFailedLoginAttempts());
        return false;
    }

    public void onSuccessfulLogin(User user) {
        user.recordSuccessfulLogin();
    }
}
