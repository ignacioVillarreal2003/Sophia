package com.sophia.notification.api.exception;

import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.application.security.SecurityUtilities;
import com.sophia.notification.application.service.AuditService;
import com.sophia.notification.domain.entity.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private MockedStatic<SecurityUtilities> securityUtilities;

    @AfterEach
    void closeStatics() {
        if (securityUtilities != null) {
            securityUtilities.close();
        }
    }

    @Test
    void deniedActionByAnAuthenticatedUserIsAuditedAsFailure() {
        UUID userId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/api/v1/notification-categories");
        stubCurrentUser(userId);

        handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        verify(auditService).record(eq(userId), eq("u@sophia.local"), eq("ACCESS_DENIED"), isNull(), isNull(),
                eq(AuditLog.Result.FAILURE), isNull());
    }

    @Test
    void unauthenticatedFailuresAreNotAudited() {
        when(request.getRequestURI()).thenReturn("/api/v1/notification-categories");
        stubCurrentUser(null);

        handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        verifyNoInteractions(auditService);
    }

    private void stubCurrentUser(UUID userId) {
        securityUtilities = mockStatic(SecurityUtilities.class);
        if (userId == null) {
            securityUtilities.when(SecurityUtilities::currentUser).thenReturn(Optional.empty());
        } else {
            AuthenticatedUser user = new AuthenticatedUser(userId, "u@sophia.local", Set.of(), Set.of());
            securityUtilities.when(SecurityUtilities::currentUser).thenReturn(Optional.of(user));
        }
    }
}
