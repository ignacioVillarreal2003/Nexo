package com.nexo.delivery.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.domain.entity.EmailRequest;
import com.nexo.delivery.domain.entity.enums.DeliveryRelation;
import com.nexo.delivery.infrastructure.messaging.NotificationCommandPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Arma el comando para notification con un correo de resumen: cada noticia con sus textos en español y en inglés
 * (notification elige según el idioma del lector) y, si es el resumen automático, el aviso para la campana.
 */
@Component
@RequiredArgsConstructor
class DigestComposer {

    /** Una noticia del correo, en el formato que espera notification ({@code items} en JSON). */
    record Item(String topic, String url, String source, String imageUrl, Integer readingMinutes, boolean update,
                Texts es, Texts en) {
    }

    record Texts(String title, String summary, String detailed, List<String> keyPoints, String why) {
    }

    private final ObjectMapper objectMapper;
    private final ApplicationProperties properties;

    NotificationCommandPayload compose(EmailRequest request, List<Delivery> items) {
        LocalDate date = request.getDeliveryDate();
        boolean onDemand = request.isOnDemand();
        int count = items.size();
        return new NotificationCommandPayload(1, UUID.randomUUID(), Instant.now(),
                onDemand ? NotificationCommandPayload.CATEGORY_ON_DEMAND : NotificationCommandPayload.CATEGORY_DAILY_DIGEST,
                null,
                new NotificationCommandPayload.Attributes(null, null, "daily-digest:" + date, "DAY", null, null),
                new NotificationCommandPayload.Recipient(request.getUserId(), null, null),
                // "Enviarme por mail" es un pedido explícito: solo correo, sin aviso en la campana.
                onDemand ? null : inApp(date, items),
                new NotificationCommandPayload.EmailMessage(NotificationCommandPayload.TEMPLATE_DAILY_DIGEST,
                        Map.of("count", String.valueOf(count), "date", date.toString(), "items", itemsJson(items)),
                        null, null));
    }

    /** El título lo reescribe notification en el idioma del lector; este queda como respaldo. */
    private static NotificationCommandPayload.InAppMessage inApp(LocalDate date, List<Delivery> items) {
        int count = items.size();
        String title = count == 1 ? "Tenés 1 noticia nueva en tu día" : "Tenés " + count + " noticias nuevas en tu día";
        String body = items.stream().limit(3).map(Delivery::getTitle).collect(Collectors.joining(" · "));
        return new NotificationCommandPayload.InAppMessage("DAILY_DIGEST", title, body,
                Map.of("date", date.toString(), "link", "/my-day?date=" + date));
    }

    private String itemsJson(List<Delivery> items) {
        List<Item> rows = items.stream().map(d -> new Item(d.getTopicName(), d.getOriginalUrl(), outlet(d.getSourceName()),
                d.getImageUrl(), d.getReadingMinutes(), d.getRelation() == DeliveryRelation.UPDATE,
                texts(d.getTitle(), d.getSummary(), d.getDetailedSummary(), d.getKeyPoints(), d.getWhyItMatters()),
                texts(d.getTitleEn(), d.getSummaryEn(), d.getDetailedSummaryEn(), d.getKeyPointsEn(),
                        d.getWhyItMattersEn()))).toList();
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the digest items", e);
        }
    }

    private Texts texts(String title, String summary, String detailed, List<String> points, String why) {
        return new Texts(title, summary, shorten(detailed, properties.digest().detailedSummaryChars()),
                points == null ? List.of() : points, why);
    }

    /** "Xataka · vía Google News (ES)" → "Xataka". */
    static String outlet(String sourceName) {
        return sourceName == null ? null : sourceName.split(" · vía ")[0].trim();
    }

    /** Corta en el último espacio antes del máximo (o en el máximo si no hay espacios) y agrega "…". */
    static String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max);
        return text.substring(0, cut > 0 ? cut : max) + "…";
    }
}
