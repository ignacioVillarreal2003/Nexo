package com.nexo.notification.infrastructure.repository;

import com.nexo.notification.domain.entity.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserContactRepository extends JpaRepository<UserContact, UUID> {
}
