package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.UserSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastActiveAtDesc(UUID userId, Instant now);

    Page<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now, Pageable pageable);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.id = :id AND s.revokedAt IS NULL")
    int revokeById(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :threshold")
    int deleteAllExpiredBefore(@Param("threshold") Instant threshold);
}
