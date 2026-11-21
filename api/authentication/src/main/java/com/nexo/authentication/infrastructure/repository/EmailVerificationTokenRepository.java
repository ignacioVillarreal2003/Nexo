package com.nexo.authentication.infrastructure.repository;

import com.nexo.authentication.domain.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int invalidateAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :threshold")
    int deleteAllExpiredBefore(@Param("threshold") Instant threshold);
}
