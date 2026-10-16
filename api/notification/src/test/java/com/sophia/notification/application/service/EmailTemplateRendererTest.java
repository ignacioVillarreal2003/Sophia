package com.sophia.notification.application.service;

import com.sophia.notification.domain.entity.NotificationCategoryView;
import com.sophia.notification.domain.exception.InvalidNotificationCommandException;
import com.sophia.notification.domain.messaging.NotificationCommand;
import com.sophia.notification.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
        when(categoryService.findByCode(eq("DOCUMENT_WORKSPACE_WORKSPACE")))
                .thenReturn(new NotificationCategoryView("DOCUMENT_WORKSPACE_WORKSPACE", "Workspace", null, false, true));
        when(categoryService.findByCode(eq("SYSTEM")))
                .thenReturn(new NotificationCategoryView("SYSTEM", "System", null, false, true));
        renderer = new EmailTemplateRenderer(TestFixtures.applicationProperties(), categoryService,
                TestFixtures.emailMessages());
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
    void rendersDocumentWorkspaceInvitationWithPersonalMessage() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(command(
                        "document-workspace.invitation",
                        Map.of("workspaceName", "Design <Team>", "inviterName", "Grace",
                                "message", "Join us!", "token", "invite-token"), null, null),
                "invitee@sophia.local", null);

        assertThat(rendered.subject()).isEqualTo("You have been invited to Design &lt;Team&gt;");
        assertThat(rendered.htmlBody())
                .contains("Grace invited you")
                .contains("Join us!")
                .contains("http://localhost:4200/invitations/accept?token=invite-token");
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
                        "DOCUMENT_WORKSPACE_WORKSPACE", null, null,
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

    private NotificationCommand command(String template, Map<String, String> variables,
                                        String subject, String htmlBody) {
        return commandWithCategory("AUTHENTICATION_SECURITY", template, variables, subject, htmlBody);
    }

    private NotificationCommand commandWithCategory(String category, String template,
                                                    Map<String, String> variables,
                                                    String subject, String htmlBody) {
        return new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, category, null, null,
                new NotificationCommand.Recipient(UUID.randomUUID(), "recipient@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage(template, variables, subject, htmlBody));
    }
}
