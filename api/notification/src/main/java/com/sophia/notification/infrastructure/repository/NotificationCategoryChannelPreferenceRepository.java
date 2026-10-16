package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.NotificationCategoryChannelPreference;
import com.sophia.notification.domain.entity.NotificationCategoryChannelPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationCategoryChannelPreferenceRepository
        extends JpaRepository<NotificationCategoryChannelPreference, NotificationCategoryChannelPreferenceId> {

    List<NotificationCategoryChannelPreference> findAllByIdUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM NotificationCategoryChannelPreference p WHERE p.id.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
