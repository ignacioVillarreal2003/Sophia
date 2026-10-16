package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    Optional<UploadSession> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<UploadSession> findAllByStatusAndExpiresAtBefore(UploadSession.Status status,
                                                          Instant threshold);
}
