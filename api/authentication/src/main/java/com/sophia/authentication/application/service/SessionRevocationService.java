package com.sophia.authentication.application.service;

import com.sophia.authentication.infrastructure.repository.RefreshTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeSessionFamily(UUID sessionId) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllBySessionId(sessionId, now);
        userSessionRepository.revokeById(sessionId, now);
    }
}
