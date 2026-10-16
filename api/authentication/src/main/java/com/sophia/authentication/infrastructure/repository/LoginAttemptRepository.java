package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Modifying
    @Query("DELETE FROM LoginAttempt la WHERE la.createdAt < :threshold")
    int deleteAllBefore(@Param("threshold") Instant threshold);
}
