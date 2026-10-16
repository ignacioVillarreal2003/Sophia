package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.LoginAttempt;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.exception.TooManyRequestsException;
import com.sophia.authentication.infrastructure.repository.LoginAttemptRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.security.LoginFailureBudget;
import com.sophia.authentication.application.service.LoginAttemptService;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private UserRepository userRepository;
    @Mock private LoginFailureBudget loginFailureBudget;

    private LoginAttemptService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(loginAttemptRepository, userRepository, loginFailureBudget,
                TestFixtures.appProperties(), TestFixtures.authenticationMetrics());
        user = TestFixtures.user("jane@test.local", "hash");
    }

    @Test
    @DisplayName("locks the account when the failure threshold is reached")
    void locksAtThreshold() {
        user.recordFailedLoginAttempt();
        user.recordFailedLoginAttempt();
        user.recordFailedLoginAttempt();

        assertThat(service.onFailedLogin(user)).isFalse();
        assertThat(service.onFailedLogin(user)).isTrue();
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository).updateFailedLoginAttempts(user.getId(), 4);
        verify(userRepository).lockAccount(eq(user.getId()), any(Instant.class));
    }

    @Test
    @DisplayName("a successful login clears counters and updates last login")
    void successClearsState() {
        user.recordFailedLoginAttempt();
        user.recordFailedLoginAttempt();
        user.recordFailedLoginAttempt();
        user.recordFailedLoginAttempt();

        service.onSuccessfulLogin(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("a failed attempt is persisted and consumes the IP budget")
    void failedAttemptConsumesBudget() {
        service.record("Jane@test.local", "10.0.0.9", "agent", false, "BAD_CREDENTIALS");

        ArgumentCaptor<LoginAttempt> attempt = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getEmail()).isEqualTo("jane@test.local");
        verify(loginFailureBudget).recordFailure(eq("10.0.0.9"), any(Duration.class));
    }

    @Test
    @DisplayName("a successful attempt does not consume the IP budget")
    void successfulAttemptSkipsBudget() {
        service.record("jane@test.local", "10.0.0.9", "agent", true, null);

        verify(loginAttemptRepository).save(any(LoginAttempt.class));
        verifyNoInteractions(loginFailureBudget);
    }

    @Test
    @DisplayName("blocks IPs that exhausted the failure budget")
    void blocksAbusiveIp() {
        when(loginFailureBudget.failureCount("10.0.0.9")).thenReturn(25L);

        assertThatThrownBy(() -> service.assertIpNotBlocked("10.0.0.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    @DisplayName("allows IPs under the failure budget")
    void allowsNormalIp() {
        when(loginFailureBudget.failureCount("10.0.0.9")).thenReturn(3L);

        assertThatCode(() -> service.assertIpNotBlocked("10.0.0.9")).doesNotThrowAnyException();
    }
}
