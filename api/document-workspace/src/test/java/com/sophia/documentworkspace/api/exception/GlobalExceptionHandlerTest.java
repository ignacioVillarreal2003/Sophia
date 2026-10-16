package com.sophia.documentworkspace.api.exception;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.SecurityUtilities;
import com.sophia.documentworkspace.application.service.AuditService;
import com.sophia.documentworkspace.domain.entity.AuditLog;
import com.sophia.documentworkspace.domain.exception.ApiException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler handler;

    private MockedStatic<SecurityUtilities> securityUtils;

    @AfterEach
    void closeStatics() {
        if (securityUtils != null) {
            securityUtils.close();
        }
    }

    @Test
    void deniedActionByAnAuthenticatedUserIsAuditedAsFailure() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces/" + workspaceId + "/artifacts");
        stubCurrentUser(userId);

        ApiException ex = new ApiException(ErrorCode.INSUFFICIENT_ROLE, HttpStatus.FORBIDDEN,
                "Your role does not allow this operation", Map.of("requiredPermission", "MANAGE_WORKSPACE"));

        handler.handleApiException(ex, request);

        verify(auditService).record(eq(userId), eq("u@sophia.local"), eq("INSUFFICIENT_ROLE"),
                isNull(), isNull(), eq(AuditLog.Result.FAILURE), eq(ex.getDetails()), eq(workspaceId));
    }

    @Test
    void unauthenticatedFailuresAreNotAudited() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces/" + UUID.randomUUID() + "/artifacts");
        stubCurrentUser(null);

        handler.handleApiException(new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "no user"),
                request);

        verifyNoInteractions(auditService);
    }

    @Test
    void resourceTypeAndIdAreLiftedFromExceptionDetails() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces/" + workspaceId + "/artifacts/" + artifactId);
        stubCurrentUser(userId);

        handler.handleApiException(new ResourceNotFoundException("Artifact", artifactId), request);

        verify(auditService).record(eq(userId), eq("u@sophia.local"), eq("RESOURCE_NOT_FOUND"),
                eq("Artifact"), eq(artifactId.toString()), eq(AuditLog.Result.FAILURE), any(), eq(workspaceId));
    }

    @Test
    void nonWorkspaceScopedRoutesAuditWithANullWorkspaceId() {
        UUID userId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");
        stubCurrentUser(userId);

        handler.handleApiException(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "bad"),
                request);

        verify(auditService).record(eq(userId), eq("u@sophia.local"), eq("VALIDATION_FAILED"),
                isNull(), isNull(), eq(AuditLog.Result.FAILURE), any(), isNull());
    }

    @Test
    void validationFailureListsFieldErrors() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("request", "name", "must not be blank")));

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().getFirst().field()).isEqualTo("name");
    }

    @Test
    void constraintViolationListsFieldErrors() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleConstraintViolation(new ConstraintViolationException("invalid", Set.of()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
    }

    @Test
    void typeMismatchNamesTheOffendingParameter() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("page");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("page");
    }

    @Test
    void missingParameterNamesTheMissingParameter() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleMissingParameter(
                        new MissingServletRequestParameterException("search", "String"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("search");
    }

    @Test
    void unreadableBodyIsReportedAsMalformed() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleUnreadableBody(new HttpMessageNotReadableException("bad json"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.MALFORMED_REQUEST.name());
    }

    @Test
    void accessDeniedMapsToForbidden() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.ACCESS_DENIED.name());
    }

    @Test
    void optimisticLockMapsToConflict() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleOptimisticLock(new OptimisticLockingFailureException("stale"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.name());
    }

    @Test
    void dataIntegrityViolationMapsToConflict() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");
        when(request.getMethod()).thenReturn("POST");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("duplicate"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.CONFLICT.name());
    }

    @Test
    void maxUploadSizeMapsToPayloadTooLarge() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleMaxUpload(new MaxUploadSizeExceededException(1024L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.FILE_TOO_LARGE.name());
    }

    @Test
    void unknownRouteMapsToNotFound() {
        when(request.getRequestURI()).thenReturn("/api/v1/does-not-exist");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "/does-not-exist"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.name());
    }

    @Test
    void unsupportedMethodMapsToMethodNotAllowed() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.name());
    }

    @Test
    void unexpectedFailuresMapToInternalError() {
        when(request.getRequestURI()).thenReturn("/api/v1/workspaces");
        when(request.getMethod()).thenReturn("GET");

        ResponseEntity<com.sophia.documentworkspace.domain.dto.response.ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
    }

    private void stubCurrentUser(UUID userId) {
        securityUtils = mockStatic(SecurityUtilities.class);
        if (userId == null) {
            securityUtils.when(SecurityUtilities::currentUser).thenReturn(Optional.empty());
        } else {
            AuthenticatedUser user = new AuthenticatedUser(userId, "u@sophia.local", Set.of(), Set.of());
            securityUtils.when(SecurityUtilities::currentUser).thenReturn(Optional.of(user));
        }
    }
}
