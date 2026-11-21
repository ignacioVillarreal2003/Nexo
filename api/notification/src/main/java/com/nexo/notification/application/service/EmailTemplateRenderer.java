package com.nexo.notification.application.service;

import com.nexo.notification.domain.config.ApplicationProperties;
import com.nexo.notification.domain.exception.InvalidNotificationCommandException;
import com.nexo.notification.domain.messaging.EmailTemplate;
import com.nexo.notification.domain.messaging.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nexo.notification.application.service.EmailHtml.escape;

/**
 * Arma el asunto y el cuerpo HTML de cada correo: las plantillas de {@code templates/email/messages*.properties}
 * con sus variables (escapadas) y el marco común (pie y enlace a las preferencias).
 */
@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    public record RenderedEmail(String subject, String htmlBody) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final ApplicationProperties properties;
    private final NotificationCategoryService categoryService;
    private final MessageSource emailMessages;
    private final DailyDigestRenderer dailyDigestRenderer;

    public RenderedEmail render(NotificationCommand command, String displayName, Locale locale) {
        Locale resolved = locale == null ? Locale.ENGLISH : locale;
        NotificationCommand.EmailMessage email = command.email();
        if (email.isTemplated()) {
            EmailTemplate template = EmailTemplate.fromKey(email.template())
                    .orElseThrow(() -> new InvalidNotificationCommandException(
                            "Unknown email template: " + email.template()));
            Map<String, String> variables = email.variables() == null ? Map.of() : email.variables();
            return renderTemplate(template, variables, displayName, command.category(), resolved);
        }
        if (email.subject() == null || email.subject().isBlank()
                || email.htmlBody() == null || email.htmlBody().isBlank()) {
            throw new InvalidNotificationCommandException(
                    "Email message requires either a template or an explicit subject and body");
        }
        return new RenderedEmail(email.subject(), wrap(email.htmlBody(), command.category(), resolved));
    }

    /** Título del aviso in-app del resumen diario, en el idioma de quien lo recibe. */
    public String dailyDigestInAppTitle(String count, Locale locale) {
        Locale resolved = locale == null ? Locale.ENGLISH : locale;
        return message("delivery.daily-digest.inapp-title", resolved).replace("{count}", count);
    }

    private RenderedEmail renderTemplate(EmailTemplate template, Map<String, String> variables,
                                         String displayName, String category, Locale locale) {
        Map<String, String> values = new LinkedHashMap<>();
        variables.forEach((key, value) -> values.put(key, escape(value)));
        values.put("name", escape(variables.getOrDefault("name", displayName)));

        switch (template) {
            case SECURITY_EMAIL_VERIFICATION -> {
                values.put("link", frontendLink(properties.frontend().verifyEmailPath(),
                        require(template, variables, "token")));
                values.putIfAbsent("ttlHours", "24");
            }
            case SECURITY_PASSWORD_RESET -> {
                values.put("link", frontendLink(properties.frontend().resetPasswordPath(),
                        require(template, variables, "token")));
                values.putIfAbsent("ttlMinutes", "30");
            }
            case SECURITY_PASSWORD_CHANGED -> {
            }
            case DELIVERY_DAILY_DIGEST -> prepareDailyDigest(template, variables, values, locale);
            case GENERIC_NOTIFICATION -> {
                values.put("link", escape(properties.frontend().baseUrl()));
                if (!values.containsKey("title") || values.get("title").isBlank()) {
                    values.put("title", message(template.key() + ".default-title", locale));
                }
                values.putIfAbsent("body", "");
            }
        }

        String subject = fill(message(template.key() + ".subject", locale), values);
        String body = fill(message(template.key() + ".body", locale), values);
        return new RenderedEmail(subject, wrap(body, category, locale));
    }

    /** Resumen diario: saludo, fecha en el idioma del correo, enlace a Mi día y el bloque con las noticias. */
    private void prepareDailyDigest(EmailTemplate template, Map<String, String> variables,
                                    Map<String, String> values, Locale locale) {
        values.put("count", escape(require(template, variables, "count")));
        String date = require(template, variables, "date");
        values.put("day", escape(formatDay(date, locale)));
        // Sin nombre real (por ejemplo, alguien que se registró solo con el correo) se saluda sin nombre.
        String name = values.getOrDefault("name", ""); // ya escapado
        values.put("greeting", message(name.isBlank() || name.contains("@")
                ? "delivery.daily-digest.greeting" : "delivery.daily-digest.greeting-named", locale)
                .replace("{name}", name));
        values.put("link", escape(UriComponentsBuilder.fromUriString(properties.frontend().baseUrl())
                .path(properties.frontend().myDayPath())
                .queryParam("date", date)
                .build()
                .encode()
                .toUriString()));
        values.put("itemsBlock", dailyDigestRenderer.render(variables.get("items"), locale));
    }

    private static String formatDay(String isoDate, Locale locale) {
        try {
            String formatted = LocalDate.parse(isoDate)
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale));
            return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
        } catch (DateTimeParseException e) {
            return isoDate;
        }
    }

    private String message(String code, Locale locale) {
        try {
            return emailMessages.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            throw new IllegalStateException("Missing email template text for '" + code + "'", e);
        }
    }

    private static String fill(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder filled = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(filled, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(filled);
        return filled.toString();
    }

    private static String require(EmailTemplate template, Map<String, String> variables, String variable) {
        String value = variables.get(variable);
        if (value == null || value.isBlank()) {
            throw new InvalidNotificationCommandException(
                    "Template %s requires the '%s' variable".formatted(template.key(), variable));
        }
        return value;
    }

    private String frontendLink(String path, String token) {
        return escape(UriComponentsBuilder.fromUriString(properties.frontend().baseUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString());
    }

    private String wrap(String body, String category, Locale locale) {
        String settingsLine = category != null && categoryService.findByCode(category).mandatory()
                ? ""
                : "<p style=\"color: #888; font-size: 12px;\"><a href=\"%s\" style=\"color: #888;\">%s</a></p>"
                .formatted(escape(UriComponentsBuilder.fromUriString(properties.frontend().baseUrl())
                                .path(properties.frontend().notificationSettingsPath())
                                .build()
                                .encode()
                                .toUriString()),
                        message("layout.preferences", locale));
        return """
                <html><body style="margin: 0; padding: 0; font-family: Arial, sans-serif; color: #222;">
                <div style="max-width: 640px; margin: 0 auto; padding: 24px 16px;">
                %s
                <hr style="border: none; border-top: 1px solid #eee;"/>
                <p style="color: #888; font-size: 12px;">%s</p>
                %s
                </div>
                </body></html>
                """.formatted(body, message("layout.footer", locale), settingsLine);
    }
}
