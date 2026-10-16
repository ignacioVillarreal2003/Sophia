package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM audit_logs WHERE id IN (
                SELECT id FROM audit_logs WHERE created_at < :threshold LIMIT :batchSize)
            """)
    int deleteBatchOfEntriesBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
