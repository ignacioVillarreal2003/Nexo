package com.nexo.notification.support;

import com.nexo.notification.domain.config.ApplicationProperties;
import com.nexo.notification.infrastructure.config.EmailMessagesConfiguration;
import org.springframework.context.MessageSource;

import java.time.Duration;
import java.util.List;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static MessageSource emailMessages() {
        return new EmailMessagesConfiguration().emailMessages();
    }

    public static ApplicationProperties applicationProperties() {
        return applicationProperties(true);
    }

    public static ApplicationProperties applicationProperties(boolean emailEnabled) {
        return new ApplicationProperties(
                new ApplicationProperties.Security(
                        new ApplicationProperties.Security.JsonWebToken(null, "0123456789012345678901234567890123456789", "nexo-authentication"),
                        new ApplicationProperties.Security.Cors(List.of("http://localhost:4200"),
                                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"), List.of("*"),
                                List.of("X-Request-Id"), true, 3600),
                        new ApplicationProperties.Security.RateLimit(true, 300, Duration.ofMinutes(1))),
                new ApplicationProperties.Commands(
                        "nexo.notifications.commands", "nexo.notifications.commands.dlt", 6, 1, 3),
                new ApplicationProperties.UserEvents(
                        "nexo.authentication.user-events", "nexo.authentication.user-events.dlt", 1, 2),
                new ApplicationProperties.Email(
                        emailEnabled, "no-reply@nexo.local", Duration.ofSeconds(10), 5, Duration.ofMinutes(1),
                        Duration.ofSeconds(30), 20, Duration.ofHours(1)),
                new ApplicationProperties.Stream(Duration.ofSeconds(20)),
                new ApplicationProperties.Frontend(
                        "http://localhost:4200", "/verify-email", "/reset-password",
                        "/my-day", "/settings/notifications"),
                new ApplicationProperties.Retention(
                        Duration.ofDays(90), Duration.ofDays(365), Duration.ofDays(7), Duration.ofDays(7),
                        Duration.ofDays(365)),
                new ApplicationProperties.Monitoring("prometheus", "prometheus"));
    }
}
