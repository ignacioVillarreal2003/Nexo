package com.nexo.notification.application.service;

import com.nexo.notification.application.mapper.NotificationMapper;
import com.nexo.notification.domain.config.ApplicationProperties;
import com.nexo.notification.domain.entity.EmailOutboxMessage;
import com.nexo.notification.domain.entity.Notification;
import com.nexo.notification.domain.entity.NotificationAttributes;
import com.nexo.notification.domain.entity.NotificationChannelCodes;
import com.nexo.notification.domain.entity.NotificationDispatch;
import com.nexo.notification.domain.entity.ProcessedMessage;
import com.nexo.notification.domain.entity.UserContact;
import com.nexo.notification.domain.entity.enums.NotificationSeverity;
import com.nexo.notification.domain.messaging.EmailTemplate;
import com.nexo.notification.domain.messaging.NotificationCommand;
import com.nexo.notification.infrastructure.observability.NotificationMetrics;
import com.nexo.notification.infrastructure.repository.EmailOutboxRepository;
import com.nexo.notification.infrastructure.repository.NotificationDispatchRepository;
import com.nexo.notification.infrastructure.repository.NotificationRepository;
import com.nexo.notification.infrastructure.repository.ProcessedMessageRepository;
import com.nexo.notification.infrastructure.repository.UserContactRepository;
import com.nexo.notification.infrastructure.security.ratelimit.RedisRateLimiter;
import com.nexo.notification.infrastructure.service.ServerSentEventRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nexo.notification.infrastructure.messaging.MessageCorrelation;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Procesa cada comando de notificación una sola vez (idempotencia por {@code messageId}): lo valida, lo deja en la
 * bandeja de la app si corresponde y encola el correo respetando las preferencias, la categoría y el límite por
 * destinatario.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCommandProcessor {

    private static final int MAX_SUBJECT_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final UserContactRepository userContactRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationCategoryService categoryService;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final ServerSentEventRegistry serverSentEventRegistry;
    private final RedisRateLimiter rateLimiter;
    private final NotificationMetrics metrics;
    private final ApplicationProperties properties;
    private final NotificationMapper notificationMapper;
    private final NotificationCommandValidator validator;

    @Transactional
    public void process(NotificationCommand command) {
        validator.validate(command);
        if (processedMessageRepository.existsById(command.messageId())) {
            metrics.commandDuplicate(command.category());
            log.debug("Skipping already processed notification command {}", command.messageId());
            return;
        }
        processedMessageRepository.save(ProcessedMessage.of(command.messageId()));

        UUID userId = command.recipient().userId();
        UserContact contact = userId == null
                ? null
                : userContactRepository.findById(userId).orElse(null);

        Instant deliverAt = deliveryInstant(command);
        UUID notificationId = null;
        if (command.inApp() != null && preferenceService.inAppAllowed(userId, command.category())) {
            notificationId = deliverInApp(command, userId, deliverAt, localeOf(command, contact));
        }
        if (command.email() != null
                && (userId == null || preferenceService.emailAllowed(userId, command.category()))) {
            deliverEmail(command, contact, notificationId, deliverAt);
        }
        metrics.commandProcessed(command.category());
    }

    private Instant deliveryInstant(NotificationCommand command) {
        Instant scheduledAt = command.scheduledAt();
        return scheduledAt == null || !scheduledAt.isAfter(Instant.now()) ? null : scheduledAt;
    }

    private UUID deliverInApp(NotificationCommand command, UUID userId, Instant deliverAt, Locale locale) {
        NotificationCommand.InAppMessage inApp = command.inApp();
        String title = inApp.title();
        // The daily digest comes from delivery without knowing the reader's language; its title is written here.
        if (command.email() != null && EmailTemplate.DELIVERY_DAILY_DIGEST.key().equals(command.email().template())
                && command.email().variables() != null && command.email().variables().get("count") != null) {
            title = emailTemplateRenderer.dailyDigestInAppTitle(command.email().variables().get("count"), locale);
        }
        Notification notification = notificationRepository.save(Notification.of(
                userId,
                command.actorId(),
                command.category(),
                inApp.type() == null || inApp.type().isBlank() ? command.category() : inApp.type(),
                title,
                inApp.body(),
                inApp.data(),
                toAttributes(command),
                deliverAt));

        NotificationDispatch dispatch = NotificationDispatch.create(
                notification.getId(), command.messageId(), MessageCorrelation.current(), userId,
                NotificationChannelCodes.IN_APP);
        dispatch.markSent();
        dispatchRepository.save(dispatch);

        metrics.inAppCreated(command.category());
        if (deliverAt == null) {
            pushAfterCommit(userId, notification);
        }
        return notification.getId();
    }

    private void deliverEmail(NotificationCommand command, UserContact contact, UUID notificationId,
                              Instant deliverAt) {
        String address = command.recipient().email() != null && !command.recipient().email().isBlank()
                ? command.recipient().email()
                : contact == null ? null : contact.getEmail();
        if (address == null || address.isBlank()) {
            metrics.emailSkipped("unknown_recipient");
            log.warn("Dropping email for command {}: no recipient address available", command.messageId());
            return;
        }
        if (!properties.email().enabled()) {
            metrics.emailSkipped("channel_disabled");
            log.info("Email channel disabled; skipping email for command {}", command.messageId());
            return;
        }
        if (!categoryService.findByCode(command.category()).mandatory()
                && !rateLimiter.tryConsume("email:" + address,
                properties.email().perRecipientLimit(),
                properties.email().perRecipientWindow()).allowed()) {
            metrics.emailSkipped("recipient_rate_limited");
            metrics.rateLimited("email");
            log.warn("Dropping email for command {}: recipient over the per-address rate limit",
                    command.messageId());
            return;
        }
        String displayName = command.recipient().displayName() != null
                && !command.recipient().displayName().isBlank()
                ? command.recipient().displayName()
                : contact == null ? address : contact.displayName();
        EmailTemplateRenderer.RenderedEmail rendered =
                emailTemplateRenderer.render(command, displayName, localeOf(command, contact));

        NotificationDispatch dispatch = dispatchRepository.save(NotificationDispatch.create(
                notificationId, command.messageId(), MessageCorrelation.current(),
                command.recipient().userId(), NotificationChannelCodes.EMAIL));
        emailOutboxRepository.save(EmailOutboxMessage.create(
                dispatch.getId(), command.category(), address, truncateSubject(rendered.subject()),
                rendered.htmlBody(), MessageCorrelation.current(),
                deliverAt == null ? Instant.now() : deliverAt));
        metrics.emailQueued(command.category());
    }

    private Locale localeOf(NotificationCommand command, UserContact contact) {
        String tag = command.recipient().locale() != null && !command.recipient().locale().isBlank()
                ? command.recipient().locale()
                : contact == null ? null : contact.getLocale();
        return tag == null || tag.isBlank() ? null : Locale.forLanguageTag(tag);
    }

    private NotificationAttributes toAttributes(NotificationCommand command) {
        NotificationCommand.Attributes attributes = command.attributes();
        if (attributes == null) {
            return NotificationAttributes.defaults();
        }
        return new NotificationAttributes(parseSeverity(attributes.severity()), attributes.priority(),
                attributes.groupKey(), attributes.targetType(), attributes.targetId(), attributes.expiresAt());
    }

    private NotificationSeverity parseSeverity(String severity) {
        return severity == null || severity.isBlank() ? null : NotificationSeverity.valueOf(severity);
    }

    private String truncateSubject(String subject) {
        return subject != null && subject.length() > MAX_SUBJECT_LENGTH
                ? subject.substring(0, MAX_SUBJECT_LENGTH)
                : subject;
    }

    /**
     * The payload is built inside the callback on purpose: columns filled in
     * at insert time, such as the creation timestamp, are only populated once
     * the transaction has flushed.
     */
    private void pushAfterCommit(UUID recipientId, Notification notification) {
        Runnable push = () ->
                serverSentEventRegistry.publish(recipientId, "notification",
                        notificationMapper.toResponse(notification));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }
}
