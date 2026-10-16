package com.sophia.notification.infrastructure.repository;

import com.sophia.notification.domain.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, String> {
}
