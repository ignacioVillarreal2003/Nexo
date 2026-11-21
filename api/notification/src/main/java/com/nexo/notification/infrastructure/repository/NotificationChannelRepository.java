package com.nexo.notification.infrastructure.repository;

import com.nexo.notification.domain.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, String> {
}
