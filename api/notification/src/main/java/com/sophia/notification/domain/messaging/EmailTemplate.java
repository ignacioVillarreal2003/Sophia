package com.sophia.notification.domain.messaging;

import java.util.Optional;

public enum EmailTemplate {

    SECURITY_EMAIL_VERIFICATION("security.email-verification"),
    SECURITY_PASSWORD_RESET("security.password-reset"),
    SECURITY_EMAIL_CHANGE_CONFIRMATION("security.email-change-confirmation"),
    SECURITY_PASSWORD_CHANGED("security.password-changed"),
    DOCUMENT_WORKSPACE_INVITATION("document-workspace.invitation"),
    GENERIC_NOTIFICATION("generic.notification");

    private final String key;

    EmailTemplate(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<EmailTemplate> fromKey(String key) {
        for (EmailTemplate template : values()) {
            if (template.key.equals(key)) {
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }
}
