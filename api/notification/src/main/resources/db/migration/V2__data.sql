INSERT INTO notification_categories (code, name, description, is_mandatory, is_system)
VALUES ('AUTHENTICATION_SECURITY', 'Security',
        'Account security notifications (email verification, password reset/changed). Always delivered.', true, true),
       ('DOCUMENT_WORKSPACE_WORKSPACE', 'Workspace',
        'Workspace membership notifications (added/removed, invitations accepted).', false, true),
       ('DOCUMENT_WORKSPACE_SHARING', 'Sharing',
        'Notifications when content is shared with you.', false, true),
       ('DOCUMENT_WORKSPACE_MENTION', 'Mentions',
        'Notifications when you are mentioned in a comment.', false, true),
       ('DOCUMENT_WORKSPACE_COMMENT', 'Comments',
        'Notifications about new comments and replies on your content.', false, true),
       ('SYSTEM', 'System', 'Platform/system notifications.', false, true);

INSERT INTO notification_channels (code, name)
VALUES ('IN_APP', 'In-app'),
       ('EMAIL', 'Email');
