package com.nexo.notification.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.notification.domain.exception.InvalidNotificationCommandException;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.nexo.notification.application.service.EmailHtml.escape;
import static com.nexo.notification.application.service.EmailHtml.safeUrl;

/**
 * Cuerpo del resumen diario: cada noticia completa (imagen, título, medio y tiempo de lectura, el resumen completo en
 * párrafos, los puntos clave, por qué importa y el enlace a la nota), agrupada por tópico.
 *
 * <p>delivery manda cada noticia en español y en inglés; se usa el idioma del correo y, campo por campo, el otro si
 * falta. Una noticia sin informe todavía muestra su resumen corto.
 */
@Component
class DailyDigestRenderer {

    /** Una noticia del resumen, tal como la manda delivery en la variable {@code items} (JSON). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String topic, String url, String source, String imageUrl, Integer readingMinutes, boolean update,
                Texts es, Texts en) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Texts(String title, String summary, String detailed, List<String> keyPoints, String why) {
    }

    private static final String TOPIC = "<p style=\"margin: 32px 0 12px 0; padding-bottom: 6px; "
            + "border-bottom: 2px solid #c2410c; color: #c2410c; font-size: 12px; font-weight: 700; "
            + "text-transform: uppercase; letter-spacing: .08em;\">%s</p>";
    private static final String ITEM = "<div style=\"margin: 0 0 28px 0; padding: 0 0 24px 0; "
            + "border-bottom: 1px solid #eee;\">%s</div>";
    // La imagen a lo ancho, recortada a 240 px de alto (sin deformarla) para que no tape la noticia.
    private static final String IMAGE = "<a href=\"%s\" style=\"display: block; max-height: 240px; "
            + "overflow: hidden; border-radius: 8px; margin: 0 0 14px 0;\"><img src=\"%s\" alt=\"\" width=\"600\" "
            + "style=\"display: block; width: 100%%; max-width: 600px; height: auto; border: 0;\"/></a>";
    private static final String TITLE = "<h2 style=\"margin: 0 0 6px 0; font-size: 20px; line-height: 1.3;\">"
            + "<a href=\"%s\" style=\"color: #111; text-decoration: none;\">%s</a></h2>";
    private static final String META = "<p style=\"margin: 0 0 14px 0; color: #888; font-size: 12px;\">%s</p>";
    private static final String UPDATE = "<span style=\"background: #fff7ed; color: #c2410c; padding: 1px 6px; "
            + "border-radius: 4px; font-weight: 600;\">%s</span>";
    private static final String PARAGRAPH = "<p style=\"margin: 0 0 12px 0; color: #333; font-size: 15px; "
            + "line-height: 1.6;\">%s</p>";
    private static final String POINTS = "<p style=\"margin: 16px 0 6px 0; color: #111; font-size: 13px; "
            + "font-weight: 700;\">%s</p><ul style=\"margin: 0 0 12px 0; padding-left: 20px; color: #333; "
            + "font-size: 14px; line-height: 1.55;\">%s</ul>";
    private static final String POINT = "<li style=\"margin: 0 0 4px 0;\">%s</li>";
    private static final String WHY = "<div style=\"margin: 14px 0; padding: 10px 14px; background: #fff7ed; "
            + "border-left: 3px solid #c2410c; color: #333; font-size: 14px; line-height: 1.5;\">"
            + "<strong>%s</strong> %s</div>";
    private static final String LINK = "<p style=\"margin: 12px 0 0 0;\"><a href=\"%s\" style=\"color: #c2410c; "
            + "font-size: 14px; font-weight: 600; text-decoration: none;\">%s →</a></p>";
    private static final Texts EMPTY = new Texts(null, null, null, List.of(), null);
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final MessageSource emailMessages;

    DailyDigestRenderer(MessageSource emailMessages) {
        this.emailMessages = emailMessages;
    }

    /** @param itemsJson la variable {@code items} del comando: la lista de noticias en JSON */
    String render(String itemsJson, Locale locale) {
        boolean english = "en".equals(locale.getLanguage());
        Labels labels = new Labels(locale);
        Map<String, StringBuilder> byTopic = new LinkedHashMap<>();
        for (Item item : parse(itemsJson)) {
            Texts main = orEmpty(english ? item.en() : item.es());
            Texts other = orEmpty(english ? item.es() : item.en());
            String title = pick(main.title(), other.title());
            if (title.isBlank()) {
                continue;
            }
            byTopic.computeIfAbsent(item.topic() == null ? "" : item.topic(), topic -> new StringBuilder())
                    .append(ITEM.formatted(news(item, title, main, other, labels)));
        }
        StringBuilder block = new StringBuilder();
        byTopic.forEach((topic, news) -> {
            if (!topic.isBlank()) {
                block.append(TOPIC.formatted(escape(topic)));
            }
            block.append(news);
        });
        return block.toString();
    }

    private String news(Item item, String title, Texts main, Texts other, Labels labels) {
        String url = escape(safeUrl(item.url()));
        StringBuilder news = new StringBuilder();
        if (item.imageUrl() != null && item.imageUrl().startsWith("https://")) {
            news.append(IMAGE.formatted(url, escape(item.imageUrl())));
        }
        news.append(TITLE.formatted(url, escape(title)));

        List<String> meta = new ArrayList<>();
        if (item.update()) {
            meta.add(UPDATE.formatted(labels.update));
        }
        if (item.source() != null && !item.source().isBlank()) {
            meta.add(escape(item.source()));
        }
        if (item.readingMinutes() != null && item.readingMinutes() > 0) {
            meta.add(escape(labels.readingTime.replace("{minutes}", String.valueOf(item.readingMinutes()))));
        }
        if (!meta.isEmpty()) {
            news.append(META.formatted(String.join(" · ", meta)));
        }

        String detailed = pick(main.detailed(), other.detailed());
        for (String paragraph : (detailed.isBlank() ? pick(main.summary(), other.summary()) : detailed)
                .split("\\n\\s*\\n")) {
            if (!paragraph.isBlank()) {
                news.append(PARAGRAPH.formatted(escape(paragraph.strip())));
            }
        }
        List<String> points = points(main).isEmpty() ? points(other) : points(main);
        if (!points.isEmpty()) {
            StringBuilder list = new StringBuilder();
            points.forEach(p -> list.append(POINT.formatted(escape(p))));
            news.append(POINTS.formatted(labels.keyPoints, list));
        }
        String why = pick(main.why(), other.why());
        if (!why.isBlank()) {
            news.append(WHY.formatted(labels.whyItMatters, escape(why)));
        }
        news.append(LINK.formatted(url, labels.readMore));
        return news.toString();
    }

    private static List<Item> parse(String json) {
        try {
            List<Item> items = JSON.readValue(json == null ? "[]" : json, new TypeReference<>() {
            });
            return items == null ? List.of() : items;
        } catch (JsonProcessingException e) {
            throw new InvalidNotificationCommandException("The digest items are not valid JSON");
        }
    }

    private static Texts orEmpty(Texts texts) {
        return texts == null ? EMPTY : texts;
    }

    private static String pick(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback == null ? "" : fallback;
    }

    private static List<String> points(Texts texts) {
        return texts.keyPoints() == null ? List.of()
                : texts.keyPoints().stream().filter(p -> p != null && !p.isBlank()).toList();
    }

    /** Los textos fijos del resumen en el idioma del correo, ya escapados. */
    private final class Labels {
        final String readMore;
        final String keyPoints;
        final String whyItMatters;
        final String update;
        final String readingTime;

        Labels(Locale locale) {
            readMore = escape(message("delivery.daily-digest.read-more", locale));
            keyPoints = escape(message("delivery.daily-digest.key-points", locale));
            whyItMatters = escape(message("delivery.daily-digest.why-it-matters", locale));
            update = escape(message("delivery.daily-digest.update", locale));
            readingTime = message("delivery.daily-digest.reading-time", locale);
        }
    }

    private String message(String code, Locale locale) {
        try {
            return emailMessages.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            throw new IllegalStateException("Missing email template text for '" + code + "'", e);
        }
    }
}
