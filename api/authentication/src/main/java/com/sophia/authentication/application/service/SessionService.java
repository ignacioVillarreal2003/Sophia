package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.SessionResponse;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.application.mapper.SessionMapper;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import com.sophia.authentication.domain.util.AuditActions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final TokenService tokenService;
    private final SessionMapper sessionMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> listMySessions(UUID userId, UUID currentSessionId, Pageable pageable) {
        Page<UserSession> page = userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, Instant.now(), pageable);
        return PageResponse.from(page,
                session -> sessionMapper.toResponse(session, session.getId().equals(currentSessionId)));
    }

    @Transactional
    public void revokeMySession(UUID userId, UUID sessionId) {
        UserSession session = userSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        tokenService.revokeSession(session.getId());
        auditService.record(null, null, AuditActions.SESSION_REVOKED, "UserSession", sessionId.toString(),
                AuditLog.Result.SUCCESS, null);
    }

    @Transactional
    public void revokeAllMySessionsExceptCurrent(UUID userId, UUID currentSessionId) {
        tokenService.revokeAllForUserExcept(userId, currentSessionId);
        auditService.record(null, null, AuditActions.SESSIONS_REVOKED_ALL, "User", userId.toString(),
                AuditLog.Result.SUCCESS, null);
    }
}
