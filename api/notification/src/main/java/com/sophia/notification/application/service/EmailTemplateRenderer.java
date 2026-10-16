package com.sophia.notification.application.service;

import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.domain.exception.InvalidNotificationCommandException;
import com.sophia.notification.domain.messaging.EmailTemplate;
import com.sophia.notification.domain.messaging.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    public record RenderedEmail(String subject, String htmlBody) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final ApplicationProperties properties;
    private final NotificationCategoryService categoryService;
    private final MessageSource emailMessages;

    public RenderedEmail render(NotificationCommand command, String displayName, Locale locale) {
        Locale resolved = locale == null ? Locale.ENGLISH : locale;
        NotificationCommand.EmailMessage email = command.email();
        if (email.isTemplated()) {
            EmailTemplate template = EmailTemplate.fromKey(email.template())
                    .orElseThrow(() -> new InvalidNotificationCommandException(
                            "Unknown email template: " + email.template()));
            return renderTemplate(template, variablesOf(email), displayName, command.category(), resolved);
        }
        if (email.subject() == null || email.subject().isBlank()
                || email.htmlBody() == null || email.htmlBody().isBlank()) {
            throw new InvalidNotificationCommandException(
                    "Email message requires either a template or an explicit subject and body");
        }
        return new RenderedEmail(email.subject(), wrap(email.htmlBody(), command.category(), resolved));
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
            case SECURITY_EMAIL_CHANGE_CONFIRMATION -> values.put("link",
                    frontendLink(properties.frontend().confirmEmailChangePath(),
                            require(template, variables, "token")));
            case SECURITY_PASSWORD_CHANGED -> {
            }
            case DOCUMENT_WORKSPACE_INVITATION -> prepareInvitation(template, variables, values, locale);
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

    private void prepareInvitation(EmailTemplate template, Map<String, String> variables,
                                   Map<String, String> values, Locale locale) {
        values.put("workspaceName", escape(require(template, variables, "workspaceName")));
        values.put("link", frontendLink(properties.frontend().invitationPath(),
                require(template, variables, "token")));

        String inviterName = variables.get("inviterName");
        values.put("invitedBy", inviterName == null || inviterName.isBlank()
                ? message(template.key() + ".invited-by-nobody", locale)
                : fill(message(template.key() + ".invited-by-someone", locale),
                        Map.of("inviterName", escape(inviterName))));

        String personalMessage = variables.get("message");
        values.put("messageBlock", personalMessage == null || personalMessage.isBlank()
                ? ""
                : "<blockquote style=\"border-left: 3px solid #ccc; margin: 8px 0; padding-left: 12px; color: #555;\">%s</blockquote>"
                .formatted(escape(personalMessage)));

        String expiresAt = variables.get("expiresAt");
        values.put("expiryLine", expiresAt == null || expiresAt.isBlank()
                ? ""
                : fill(message(template.key() + ".expiry", locale), Map.of("expiresAt", escape(expiresAt))));
    }

    private Map<String, String> variablesOf(NotificationCommand.EmailMessage email) {
        return email.variables() == null ? Map.of() : email.variables();
    }

    private String message(String code, Locale locale) {
        try {
            return emailMessages.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            throw new IllegalStateException("Missing email template text for '" + code + "'", e);
        }
    }

    private String fill(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder filled = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(filled, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(filled);
        return filled.toString();
    }

    private String require(EmailTemplate template, Map<String, String> variables, String variable) {
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
                <html><body style="font-family: Arial, sans-serif; color: #222;">
                %s
                <hr style="border: none; border-top: 1px solid #eee;"/>
                <p style="color: #888; font-size: 12px;">%s</p>
                %s
                </body></html>
                """.formatted(body, message("layout.footer", locale), settingsLine);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
