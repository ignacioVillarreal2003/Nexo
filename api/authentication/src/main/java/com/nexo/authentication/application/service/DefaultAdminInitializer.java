package com.nexo.authentication.application.service;

import com.nexo.authentication.domain.config.ApplicationProperties;
import com.nexo.authentication.domain.entity.Role;
import com.nexo.authentication.domain.entity.enums.AuthenticationProvider;
import com.nexo.authentication.infrastructure.messaging.UserEventPublisher;
import com.nexo.authentication.infrastructure.messaging.UserEventType;
import com.nexo.authentication.infrastructure.repository.RoleRepository;
import com.nexo.authentication.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserEventPublisher userEventPublisher;
    private final ApplicationProperties properties;
    private final UserProvisioner userProvisioner;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ApplicationProperties.Admin config = properties.admin();
        if (!config.seedEnabled()) {
            return;
        }
        var existing = userRepository.findByEmailIgnoreCase(config.email());
        if (existing.isPresent()) {
            // The admin is not registered through the usual flow, so the other services (notification keeps the
            // e-mail address it writes to) learn about it from here. USER_SYNC is an idempotent upsert, which also
            // covers an admin seeded before this event existed.
            userEventPublisher.publish(UserEventType.USER_SYNC, existing.get());
            return;
        }

        Role adminRole = roleRepository.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role not found; roles seed migration must run before admin bootstrap"));

        userProvisioner.provision(new UserProvisioner.NewAccount(config.firstName(), config.lastName(),
                config.email(), config.password(), AuthenticationProvider.LOCAL, true, true, Set.of(adminRole), null));

        log.warn("Seeded default admin account ({}). Change its password immediately in production.",
                config.email());
    }
}
