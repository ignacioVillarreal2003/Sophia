package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.NotificationChannelPreference;
import com.sophia.notification.domain.entity.NotificationChannelPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationChannelPreferenceRepository
        extends JpaRepository<NotificationChannelPreference, NotificationChannelPreferenceId> {

    List<NotificationChannelPreference> findAllByIdUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM NotificationChannelPreference p WHERE p.id.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
