package com.nexo.notification.application.service;

import com.nexo.notification.domain.entity.enums.NotificationSeverity;
import com.nexo.notification.domain.exception.InvalidNotificationCommandException;
import com.nexo.notification.domain.exception.ResourceNotFoundException;
import com.nexo.notification.domain.messaging.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reglas de un comando de notificación válido: versión del esquema, categoría existente, destinatario, al menos un
 * canal y largos máximos de cada campo (los de las columnas donde se guardan). Un comando inválido no se reintenta:
 * va directo a la cola de mensajes fallidos.
 */
@Component
@RequiredArgsConstructor
class NotificationCommandValidator {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_TYPE_LENGTH = 40;
    private static final int MAX_GROUP_KEY_LENGTH = 200;
    private static final int MAX_TARGET_TYPE_LENGTH = 40;
    private static final int MAX_EMAIL_LENGTH = 255;

    private final NotificationCategoryService categoryService;

    void validate(NotificationCommand command) {
        if (command.schemaVersionOrCurrent() != NotificationCommand.CURRENT_SCHEMA_VERSION) {
            throw new InvalidNotificationCommandException(
                    "Unsupported notification command schemaVersion %d; this service speaks version %d"
                            .formatted(command.schemaVersionOrCurrent(),
                                    NotificationCommand.CURRENT_SCHEMA_VERSION));
        }
        if (command.messageId() == null) {
            throw new InvalidNotificationCommandException("Notification command requires a messageId");
        }
        if (command.category() == null || command.category().isBlank()) {
            throw new InvalidNotificationCommandException("Notification command requires a category");
        }
        try {
            categoryService.findByCode(command.category());
        } catch (ResourceNotFoundException e) {
            throw new InvalidNotificationCommandException(
                    "Unknown notification category: " + command.category());
        }
        if (command.recipient() == null) {
            throw new InvalidNotificationCommandException("Notification command requires a recipient");
        }
        if (command.inApp() == null && command.email() == null) {
            throw new InvalidNotificationCommandException(
                    "Notification command requires at least one of inApp or email");
        }
        if (command.inApp() != null) {
            if (command.recipient().userId() == null) {
                throw new InvalidNotificationCommandException(
                        "In-application notifications require a recipient userId");
            }
            if (command.inApp().title() == null || command.inApp().title().isBlank()) {
                throw new InvalidNotificationCommandException(
                        "In-application notifications require a title");
            }
            requireLength("inApp.title", command.inApp().title(), MAX_TITLE_LENGTH);
            requireLength("inApp.body", command.inApp().body(), MAX_BODY_LENGTH);
            requireLength("inApp.type", command.inApp().type(), MAX_TYPE_LENGTH);
        }
        if (command.email() != null && command.recipient().userId() == null
                && (command.recipient().email() == null || command.recipient().email().isBlank())) {
            throw new InvalidNotificationCommandException(
                    "Email notifications require a recipient userId or an explicit email address");
        }
        requireLength("recipient.email", command.recipient().email(), MAX_EMAIL_LENGTH);
        validateAttributes(command.attributes());
    }

    private void validateAttributes(NotificationCommand.Attributes attributes) {
        if (attributes == null) {
            return;
        }
        String severity = attributes.severity();
        if (severity != null && !severity.isBlank() && !NotificationSeverity.ALL.contains(severity)) {
            throw new InvalidNotificationCommandException(
                    "Unsupported notification severity '%s'; expected one of %s"
                            .formatted(severity, NotificationSeverity.ALL));
        }
        requireLength("attributes.groupKey", attributes.groupKey(), MAX_GROUP_KEY_LENGTH);
        requireLength("attributes.targetType", attributes.targetType(), MAX_TARGET_TYPE_LENGTH);
    }

    private void requireLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new InvalidNotificationCommandException(
                    "%s is %d characters long; the maximum is %d".formatted(field, value.length(), max));
        }
    }

}
