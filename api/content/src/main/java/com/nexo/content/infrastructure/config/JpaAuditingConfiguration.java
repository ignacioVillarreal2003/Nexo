package com.nexo.content.infrastructure.config;

import com.nexo.content.application.security.AuthenticatedUser;
import com.nexo.content.application.security.SecurityUtilities;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfiguration {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(SecurityUtilities.currentUser()
                .map(JpaAuditingConfiguration::auditorOf)
                .orElse("system"));
    }

    private static String auditorOf(AuthenticatedUser user) {
        if (user.service()) {
            return "service:" + user.id();
        }
        return user.email() != null ? user.email() : user.id().toString();
    }
}
