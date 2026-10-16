package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.YjsUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface YjsUpdateRepository extends JpaRepository<YjsUpdate, Long> {

    List<YjsUpdate> findAllByDocumentIdOrderByIdAsc(UUID documentId);

    long countByDocumentId(UUID documentId);

    @Modifying
    @Query("delete from YjsUpdate u where u.documentId = :documentId and u.id <= :maxId")
    int deleteByDocumentIdUpTo(@Param("documentId") UUID documentId, @Param("maxId") long maxId);

    @Modifying
    @Query("delete from YjsUpdate u where u.documentId in :documentIds")
    void deleteAllByDocumentIdIn(@Param("documentIds") Collection<UUID> documentIds);
}
