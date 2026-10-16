package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.EmailVerificationToken;
import com.sophia.authentication.domain.entity.enums.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidateAllByUserIdAndPurpose(@Param("userId") UUID userId,
                                        @Param("purpose") TokenPurpose purpose,
                                        @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :threshold")
    int deleteAllExpiredBefore(@Param("threshold") Instant threshold);
}
