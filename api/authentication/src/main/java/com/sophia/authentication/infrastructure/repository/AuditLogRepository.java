package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.createdAt < :threshold")
    int deleteAllBefore(@Param("threshold") Instant threshold);
}
