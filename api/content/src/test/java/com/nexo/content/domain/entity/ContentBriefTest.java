package com.nexo.content.domain.entity;

import com.nexo.content.domain.entity.enums.AiMode;
import com.nexo.content.domain.entity.enums.BriefStatus;
import com.nexo.content.domain.entity.enums.ContentRelation;
import com.nexo.content.domain.entity.enums.ContentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBriefTest {

    private static Content content(String url, String imageUrl) {
        return Content.builder()
                .origin(url, "hash-" + UUID.randomUUID(), "content-hash", 1L, UUID.randomUUID())
                .source(UUID.randomUUID(), "El Medio", null, imageUrl, null)
                .text("Titular original", "Título corto", "Resumen corto.", "Copete", "es")
                .analysis(List.of("energía"), List.of(), 0.8, AiMode.GEMINI, null)
                .decision(ContentStatus.PUBLISHED, ContentRelation.NEW, null, null, null)
                .build();
    }

    private static Content.Brief brief(AiMode mode) {
        return new Content.Brief("Título completo", "Full title", "Resumen mejorado.", "Better summary.",
                "Primer párrafo.\n\nSegundo párrafo.", "First.\n\nSecond.", List.of("Punto uno"), List.of("Point one"),
                "Importa.", "It matters.", 4, mode, "https://medio.uy/foto.jpg", "https://medio.uy/nota");
    }

    @Test
    void aBriefIsRequestedOnlyOnce() {
        Content content = content("https://medio.uy/nota", null);

        assertThat(content.requestBrief()).isTrue();
        assertThat(content.requestBrief()).isFalse();
        assertThat(content.getBriefStatus()).isEqualTo(BriefStatus.PENDING);
        assertThat(content.getBriefRequestedAt()).isNotNull();
    }

    @Test
    void failedAttemptsEndInFailedAfterTheMaximum() {
        Content content = content("https://medio.uy/nota", null);
        content.requestBrief();

        content.briefAttemptFailed(2);
        assertThat(content.getBriefStatus()).isEqualTo(BriefStatus.PENDING);
        content.briefAttemptFailed(2);
        assertThat(content.getBriefStatus()).isEqualTo(BriefStatus.FAILED);
        assertThat(content.requestBrief()).isFalse(); // no se vuelve a encolar solo
    }

    @Test
    void aModelBriefStoresEverythingAndImprovesTitleAndSummaryInBothLanguages() {
        Content content = content("https://news.google.com/rss/articles/abc", null);
        content.requestBrief();

        content.applyBrief(brief(AiMode.GEMINI));

        assertThat(content.hasBrief()).isTrue();
        assertThat(content.getDetailedSummary()).isEqualTo("Primer párrafo.\n\nSegundo párrafo.");
        assertThat(content.getKeyPoints()).containsExactly("Punto uno");
        assertThat(content.getKeyPointsEn()).containsExactly("Point one");
        assertThat(content.getReadingMinutes()).isEqualTo(4);
        assertThat(content.getTitle()).isEqualTo("Título completo");
        assertThat(content.getTitleEn()).isEqualTo("Full title");
        // El enlace de Google News pasa al del medio y la imagen se completa.
        assertThat(content.getOriginalUrl()).isEqualTo("https://medio.uy/nota");
        assertThat(content.getImageUrl()).isEqualTo("https://medio.uy/foto.jpg");
    }

    @Test
    void anExtractiveBriefDoesNotReplaceTheTitleNorAnExistingImageOrLink() {
        Content content = content("https://medio.uy/otra", "https://medio.uy/original.jpg");

        content.applyBrief(brief(AiMode.HEURISTIC));

        assertThat(content.hasBrief()).isTrue();
        assertThat(content.getTitle()).isEqualTo("Título corto");
        assertThat(content.getImageUrl()).isEqualTo("https://medio.uy/original.jpg");
        assertThat(content.getOriginalUrl()).isEqualTo("https://medio.uy/otra");
    }

    @Test
    void anAdministratorsEditIsKept() {
        Content content = content("https://medio.uy/nota", null);
        content.review("Título editado", "Resumen editado.", null);

        content.applyBrief(brief(AiMode.GEMINI));

        assertThat(content.getTitle()).isEqualTo("Título editado");
        assertThat(content.getSummary()).isEqualTo("Resumen editado.");
        assertThat(content.getDetailedSummary()).isNotBlank();
    }
}
