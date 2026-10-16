package com.sophia.authentication.application.service;

import com.sophia.authentication.application.mapper.SessionMapper;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.application.service.SessionService;
import com.sophia.authentication.application.service.TokenService;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.SessionResponse;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import com.sophia.authentication.support.TestFixtures;
import com.sophia.authentication.support.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private UserSessionRepository userSessionRepository;
    @Mock private TokenService tokenService;
    @Mock private AuditService auditService;

    private SessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SessionService(userSessionRepository, tokenService,
                Mappers.getMapper(SessionMapper.class), auditService);
        user = TestFixtures.user("jane@test.local", "hash");
    }

    private UserSession session(UUID id) {
        return TestIds.withId(UserSession.create(
                user, "10.0.0.1", null, "laptop", false, Instant.now(), Instant.now().plusSeconds(3600)), id);
    }

    @Test
    @DisplayName("lists active sessions as a page and flags the current one")
    void listsSessionsWithCurrentFlag() {
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                eq(user.getId()), any(Instant.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(session(currentSessionId), session(otherSessionId)),
                        pageable, 2));

        PageResponse<SessionResponse> page = service.listMySessions(user.getId(), currentSessionId, pageable);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(SessionResponse::current)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("revokes one of the caller's own sessions")
    void revokesOwnSession() {
        UUID sessionId = UUID.randomUUID();
        when(userSessionRepository.findByIdAndUserId(sessionId, user.getId()))
                .thenReturn(Optional.of(session(sessionId)));

        service.revokeMySession(user.getId(), sessionId);

        verify(tokenService).revokeSession(sessionId);
    }

    @Test
    @DisplayName("revoking a session that belongs to someone else is a 404")
    void revokingForeignSessionFails() {
        UUID sessionId = UUID.randomUUID();
        when(userSessionRepository.findByIdAndUserId(sessionId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeMySession(user.getId(), sessionId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tokenService, never()).revokeSession(any());
    }

    @Test
    @DisplayName("revokes every session except the current one")
    void revokesAllExceptCurrent() {
        UUID currentSessionId = UUID.randomUUID();

        service.revokeAllMySessionsExceptCurrent(user.getId(), currentSessionId);

        verify(tokenService).revokeAllForUserExcept(user.getId(), currentSessionId);
    }
}
