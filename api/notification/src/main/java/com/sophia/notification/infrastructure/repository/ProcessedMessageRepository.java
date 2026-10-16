package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            DELETE FROM processed_messages WHERE message_id IN (
                SELECT message_id FROM processed_messages WHERE processed_at < :threshold LIMIT :batchSize)
            """)
    int deleteBatchOfProcessedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
