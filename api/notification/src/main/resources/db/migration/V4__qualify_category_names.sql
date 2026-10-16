-- Category codes are namespaced by the service that owns them
-- (<SERVICE>_<CATEGORY>), but their display names were not: a second service
-- publishing its own "Workspace" or "Sharing" category would be
-- indistinguishable in the preferences screen. Names now mirror the code.
UPDATE notification_categories SET name = 'Authentication - Security'
WHERE code = 'AUTHENTICATION_SECURITY';

UPDATE notification_categories SET name = 'Document workspace - Workspace'
WHERE code = 'DOCUMENT_WORKSPACE_WORKSPACE';

UPDATE notification_categories SET name = 'Document workspace - Sharing'
WHERE code = 'DOCUMENT_WORKSPACE_SHARING';

UPDATE notification_categories SET name = 'Document workspace - Mentions'
WHERE code = 'DOCUMENT_WORKSPACE_MENTION';

UPDATE notification_categories SET name = 'Document workspace - Comments'
WHERE code = 'DOCUMENT_WORKSPACE_COMMENT';

-- SYSTEM is platform-wide and owned by no single service, so it stays bare.
UPDATE notification_categories SET name = 'System'
WHERE code = 'SYSTEM';
