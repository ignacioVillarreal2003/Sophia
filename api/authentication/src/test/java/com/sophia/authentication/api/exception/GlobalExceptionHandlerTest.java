package com.sophia.authentication.api.exception;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.api.exception.GlobalExceptionHandler;
import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.application.security.SecurityUtilities;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.domain.dto.response.ErrorResponse;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private GlobalExceptionHandler handler;

    private MockedStatic<SecurityUtilities> securityUtilities;

    @AfterEach
    void closeStatics() {
        if (securityUtilities != null) {
            securityUtilities.close();
        }
    }

    private void stubCurrentUser(UUID userId) {
        securityUtilities = mockStatic(SecurityUtilities.class);
        if (userId == null) {
            securityUtilities.when(SecurityUtilities::currentUser).thenReturn(Optional.empty());
        } else {
            CustomUserDetails user = new CustomUserDetails(userId, "u@sophia.local", null, true, true, null, java.util.List.of());
            securityUtilities.when(SecurityUtilities::currentUser).thenReturn(Optional.of(user));
        }
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private void assertStatus(ResponseEntity<ErrorResponse> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(expected.value());
    }

    @Test
    @DisplayName("ApiException subclasses map to their own status")
    void handlesApiException() {
        var response = handler.handleApiException(
                new ResourceNotFoundException("User", "1"), request("/api/v1/users/1"));

        assertStatus(response, HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path()).isEqualTo("/api/v1/users/1");
    }

    @Test
    @DisplayName("malformed JSON bodies become 400")
    void handlesUnreadableBody() {
        var response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null),
                request("/api/v1/authentication/login"));

        assertStatus(response, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("missing request parameters become 400 with the parameter name")
    void handlesMissingParam() {
        var response = handler.handleMissingParam(
                new MissingServletRequestParameterException("page", "int"), request("/api/v1/users"));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("page");
    }

    @Test
    @DisplayName("type mismatches become 400 with the parameter name")
    void handlesTypeMismatch() {
        var response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", UUID_CLASS, "id", null, null), request("/api/v1/users/abc"));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("id");
    }

    private static final Class<?> UUID_CLASS = java.util.UUID.class;

    @Test
    @DisplayName("unsupported HTTP methods become 405")
    void handlesMethodNotSupported() {
        var response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"), request("/api/v1/users"));

        assertStatus(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("unknown static resources become 404")
    void handlesNoResource() {
        var response = handler.handleNoResource(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing"),
                request("/missing"));

        assertStatus(response, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("oversized uploads become 413")
    void handlesMaxUpload() {
        var response = handler.handleMaxUpload(
                new MaxUploadSizeExceededException(1024), request("/api/v1/users/1/photo"));

        assertStatus(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("access denied becomes 403")
    void handlesAccessDenied() {
        var response = handler.handleAccessDenied(new AccessDeniedException("denied"), request("/api/v1/users"));

        assertStatus(response, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("authentication failures become 401")
    void handlesAuthentication() {
        var response = handler.handleAuthentication(new BadCredentialsException("bad"), request("/api/v1/authentication/login"));

        assertStatus(response, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("optimistic lock conflicts become 409")
    void handlesOptimisticLock() {
        var response = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("stale"), request("/api/v1/users/1"));

        assertStatus(response, HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("data integrity violations become 409")
    void handlesDataIntegrity() {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate", new RuntimeException("root cause")),
                request("/api/v1/users"));

        assertStatus(response, HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("an open circuit breaker becomes 503")
    void handlesCircuitOpen() {
        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("google");
        var response = handler.handleCircuitOpen(
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker), request("/api/v1/authentication/google"));

        assertStatus(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("anything unclassified becomes a generic 500")
    void handlesUnexpected() {
        var response = handler.handleUnexpected(new IllegalStateException("boom"), request("/api/v1/users"));

        assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("a failure by an authenticated user is audited")
    void deniedActionByAnAuthenticatedUserIsAuditedAsFailure() {
        UUID userId = UUID.randomUUID();
        stubCurrentUser(userId);

        handler.handleAccessDenied(new AccessDeniedException("denied"), request("/api/v1/users"));

        verify(auditService).record(eq(userId), eq("u@sophia.local"), eq("ACCESS_DENIED"),
                isNull(), isNull(), eq(AuditLog.Result.FAILURE), isNull());
    }

    @Test
    @DisplayName("unauthenticated failures are not audited")
    void unauthenticatedFailuresAreNotAudited() {
        stubCurrentUser(null);

        handler.handleAccessDenied(new AccessDeniedException("denied"), request("/api/v1/users"));

        verifyNoInteractions(auditService);
    }
}
