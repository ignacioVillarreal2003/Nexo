package com.nexo.notification.infrastructure.repository;

import com.nexo.notification.domain.entity.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationCategoryRepository
        extends JpaRepository<NotificationCategory, String>, JpaSpecificationExecutor<NotificationCategory> {
}
