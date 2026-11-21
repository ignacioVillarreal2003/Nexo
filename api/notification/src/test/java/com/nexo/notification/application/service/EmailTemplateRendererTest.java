package com.nexo.notification.application.service;

import com.nexo.notification.domain.entity.NotificationCategoryView;
import com.nexo.notification.domain.exception.InvalidNotificationCommandException;
import com.nexo.notification.domain.messaging.NotificationCommand;
import com.nexo.notification.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        NotificationCategoryService categoryService = mock(NotificationCategoryService.class);
        when(categoryService.findByCode(eq("AUTHENTICATION_SECURITY")))
                .thenReturn(new NotificationCategoryView("AUTHENTICATION_SECURITY", "Security", null, true, true));
        when(categoryService.findByCode(eq("CONTENT_UPDATES")))
                .thenReturn(new NotificationCategoryView("CONTENT_UPDATES", "Content updates", null, false, true));
        when(categoryService.findByCode(eq("SYSTEM")))
                .thenReturn(new NotificationCategoryView("SYSTEM", "System", null, false, true));
        renderer = new EmailTemplateRenderer(TestFixtures.applicationProperties(), categoryService,
                TestFixtures.emailMessages(), new DailyDigestRenderer(TestFixtures.emailMessages()));
    }

    @Test
    void rendersEmailVerificationTemplateWithLink() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "security.email-verification",
                        Map.of("name", "Ada", "token", "raw-token", "ttlHours", "24"), null, null),
                "Fallback", null);

        assertThat(rendered.subject()).isEqualTo("Verify your email address");
        assertThat(rendered.htmlBody())
                .contains("Hi Ada,")
                .contains("http://localhost:4200/verify-email?token=raw-token")
                .contains("24 hours");
    }

    @Test
    void rendersPasswordResetTemplate() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "security.password-reset",
                        Map.of("name", "Ada", "token", "raw-token", "ttlMinutes", "30"), null, null),
                "Fallback", null);

        assertThat(rendered.subject()).isEqualTo("Reset your password");
        assertThat(rendered.htmlBody())
                .contains("http://localhost:4200/reset-password?token=raw-token")
                .contains("30 minutes");
    }

    @Test
    void rendersDailyDigestWithEscapedItemsAndDayLink() {
        String items = """
                [{"topic":"Astronomía","url":"https://example.com/a","es":{"title":"Webb <observa> una galaxia"}},
                 {"topic":"IA","url":"javascript:alert(1)","es":{"title":"Enlace peligroso"}}]""";
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "delivery.daily-digest", Map.of("count", "2", "date", "2026-11-07", "items", items), null, null),
                "Ada", null);

        assertThat(rendered.subject()).isEqualTo("Your day on Nexo: 2 new stories");
        assertThat(rendered.htmlBody())
                .contains("Webb &lt;observa&gt; una galaxia")
                .contains("https://example.com/a")
                .doesNotContain("javascript:")
                .contains("http://localhost:4200/my-day?date=2026-11-07")
                .contains("Hi Ada,")
                .contains("Saturday, November 7, 2026");
    }

    @Test
    void dailyDigestWithoutBriefShowsTheShortSummaryAndALinkToTheStory() {
        String items = """
                [{"topic":"IA","url":"https://example.com/n",
                  "es":{"title":"Un modelo nuevo","summary":"Resumen <corto> de la nota."}}]""";
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "delivery.daily-digest", Map.of("count", "1", "date", "2026-11-07", "items", items), null, null),
                "Ana", Locale.forLanguageTag("es"));

        assertThat(rendered.htmlBody())
                .contains("Resumen &lt;corto&gt; de la nota.")
                .contains("Leer la nota original")
                .contains("href=\"https://example.com/n\"");
    }

    @Test
    void dailyDigestInAppTitleFollowsTheReadersLanguage() {
        assertThat(renderer.dailyDigestInAppTitle("3", Locale.forLanguageTag("es")))
                .isEqualTo("Tenés 3 noticias nuevas en tu día");
        assertThat(renderer.dailyDigestInAppTitle("3", Locale.forLanguageTag("en-US")))
                .isEqualTo("3 new items in your day");
    }

    @Test
    void dailyDigestGroupsItemsByTopicAndGreetsWithoutAnEmailAsName() {
        String items = """
                [{"topic":"Cloud","url":"https://example.com/1","es":{"title":"Uno"}},
                 {"topic":"Cloud","url":"https://example.com/2","es":{"title":"Dos"}},
                 {"topic":"IA","url":"https://example.com/3","es":{"title":"Tres"}}]""";
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "delivery.daily-digest", Map.of("count", "3", "date", "2026-11-07", "items", items), null, null),
                "someone@example.com", Locale.forLanguageTag("es"));

        String html = rendered.htmlBody();
        assertThat(html).contains("Hola:").doesNotContain("someone@example.com").contains("Sábado, 7 de noviembre de 2026");
        assertThat(html.split(">Cloud<", -1)).hasSize(2); // el tópico aparece una sola vez
        assertThat(html.indexOf("Uno")).isLessThan(html.indexOf("Dos"));
        assertThat(html.indexOf("Dos")).isLessThan(html.indexOf(">IA<"));
    }

    @Test
    void escapesHtmlInVariables() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "security.password-changed",
                        Map.of("name", "<script>alert(1)</script>"), null, null),
                "Fallback", null);

        assertThat(rendered.htmlBody())
                .contains("&lt;script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void genericTemplateLinksBackToTheApplication() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "generic.notification",
                        Map.of("title", "You were mentioned", "body", "In a comment"), null, null),
                "Ada", null);

        assertThat(rendered.subject()).isEqualTo("You were mentioned");
        assertThat(rendered.htmlBody())
                .contains("You were mentioned")
                .contains("In a comment")
                .contains("http://localhost:4200");
    }

    @Test
    void mandatoryCategoryOmitsPreferencesFooter() {
        EmailTemplateRenderer.RenderedEmail security = renderer.render(command(
                        "security.password-changed", Map.of("name", "Ada"), null, null),
                "Fallback", null);
        assertThat(security.htmlBody()).doesNotContain("/settings/notifications");

        EmailTemplateRenderer.RenderedEmail workspace = renderer.render(commandWithCategory(
                        "CONTENT_UPDATES", null, null,
                        "Mentioned", "<p>Hello</p>"),
                "Fallback", null);
        assertThat(workspace.htmlBody()).contains("/settings/notifications");
    }

    @Test
    void rawEmailIsWrappedInLayout() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(commandWithCategory(
                        "SYSTEM", null, null,
                        "Custom subject", "<p>Custom body</p>"),
                "Fallback", null);

        assertThat(rendered.subject()).isEqualTo("Custom subject");
        assertThat(rendered.htmlBody())
                .contains("<p>Custom body</p>")
                .contains("automated message");
    }

    @Test
    void templatesAreRenderedInTheRecipientLocale() {
        EmailTemplateRenderer.RenderedEmail spanish = renderer.render(command(
                        "security.password-reset",
                        Map.of("name", "Ada", "token", "raw-token", "ttlMinutes", "30"), null, null),
                "Fallback", java.util.Locale.forLanguageTag("es"));

        assertThat(spanish.subject()).isEqualTo("Restablecé tu contraseña");
        assertThat(spanish.htmlBody())
                .contains("Hola Ada:")
                .contains("mensaje automático")
                .contains("http://localhost:4200/reset-password?token=raw-token");
    }

    @Test
    void anUnsupportedLocaleFallsBackToTheDefaultText() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "security.password-changed", Map.of("name", "Ada"), null, null),
                "Fallback", java.util.Locale.forLanguageTag("fi"));

        assertThat(rendered.subject()).isEqualTo("Your password was changed");
    }

    @Test
    void unknownTemplateIsRejected() {
        assertThatThrownBy(() -> renderer.render(command("no.such.template", Map.of(), null, null), "x", null))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("Unknown email template");
    }

    @Test
    void missingRequiredVariableIsRejected() {
        assertThatThrownBy(() -> renderer.render(command(
                        "security.email-verification", Map.of("name", "Ada"), null, null), "x", null))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("token");
    }

    @Test
    void rawEmailWithoutSubjectIsRejected() {
        assertThatThrownBy(() -> renderer.render(commandWithCategory(
                        "SYSTEM", null, null, null, "<p>body</p>"), "x", null))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("subject");
    }

    private static final String RICH_ITEMS = """
            [{"topic":"Energía","url":"https://medio.uy/nota","source":"El Medio","imageUrl":"https://medio.uy/f.jpg",
              "readingMinutes":4,"update":true,
              "es":{"title":"Plan <eléctrico>","summary":"Breve.","detailed":"Primer párrafo.\\n\\nSegundo párrafo.",
                    "keyPoints":["Inversión de 10 millones","Obras en 2027"],"why":"Baja la tarifa."},
              "en":{"title":"Power plan","summary":"Short.","detailed":null,"keyPoints":[],"why":null}},
             {"topic":"Energía","url":"javascript:alert(1)","source":null,"imageUrl":null,"readingMinutes":null,
              "update":false,"es":{"title":"Sin informe","summary":"Solo el resumen corto.","detailed":null,
              "keyPoints":[],"why":null},"en":{"title":null,"summary":null,"detailed":null,"keyPoints":[],"why":null}}]
            """;

    @Test
    void rendersTheFullDigestInTheReadersLanguage() {
        Map<String, String> variables = Map.of("count", "2", "date", "2026-11-07", "items", RICH_ITEMS);

        String spanish = renderer.render(commandWithCategory("SYSTEM", "delivery.daily-digest", variables, null, null),
                "Ada", Locale.forLanguageTag("es")).htmlBody();
        assertThat(spanish)
                .contains("Plan &lt;eléctrico&gt;")
                .contains(">Primer párrafo.</p>").contains(">Segundo párrafo.</p>")
                .contains("Lo clave").contains("Inversión de 10 millones")
                .contains("Por qué importa:").contains("Baja la tarifa.")
                .contains("4 min de lectura").contains("Actualización").contains("El Medio")
                .contains("src=\"https://medio.uy/f.jpg\"")
                .contains(">Solo el resumen corto.</p>")
                .doesNotContain("javascript:");

        // En inglés: lo que está traducido sale en inglés y, campo por campo, lo que falta sale en español.
        String english = renderer.render(commandWithCategory("SYSTEM", "delivery.daily-digest", variables, null, null),
                "Ada", Locale.ENGLISH).htmlBody();
        assertThat(english)
                .contains("Power plan").contains("Key points").contains("Why it matters:").contains("4 min read")
                .contains(">Primer párrafo.</p>")
                .contains("Sin informe");
    }

    private NotificationCommand command(String template, Map<String, String> variables,
                                        String subject, String htmlBody) {
        return commandWithCategory("AUTHENTICATION_SECURITY", template, variables, subject, htmlBody);
    }

    private NotificationCommand commandWithCategory(String category, String template,
                                                    Map<String, String> variables,
                                                    String subject, String htmlBody) {
        return new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, category, null, null,
                new NotificationCommand.Recipient(UUID.randomUUID(), "recipient@nexo.local", null, null),
                null,
                new NotificationCommand.EmailMessage(template, variables, subject, htmlBody));
    }
}
