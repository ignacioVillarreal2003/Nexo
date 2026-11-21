package com.nexo.authentication.infrastructure.repository;

import com.nexo.authentication.domain.entity.OAuthAccount;
import com.nexo.authentication.domain.entity.enums.AuthenticationProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(AuthenticationProvider provider, String providerUserId);

    Optional<OAuthAccount> findByUserIdAndProvider(UUID userId, AuthenticationProvider provider);

    boolean existsByUserIdAndProvider(UUID userId, AuthenticationProvider provider);

    void deleteByUserId(UUID userId);
}
