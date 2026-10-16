INSERT INTO permissions (name, description, category, created_by) VALUES
    ('USER_READ',             'View users',                                'USERS',       'system'),
    ('USER_CREATE',           'Create users',                              'USERS',       'system'),
    ('USER_UPDATE',           'Update users',                              'USERS',       'system'),
    ('USER_DELETE',           'Delete users',                              'USERS',       'system'),
    ('USER_PASSWORD_RESET',   'Set another user''s password directly',     'USERS',       'system'),
    ('USER_EMAIL_VERIFY',     'Resend verification email or mark a user''s email as verified', 'USERS', 'system'),
    ('USER_GOOGLE_UNLINK',    'Force-unlink a user''s Google account',     'USERS',       'system'),
    ('ROLE_READ',             'View roles',                                'ROLES',       'system'),
    ('ROLE_MANAGE',           'Create, update, delete and assign roles',   'ROLES',       'system'),
    ('PERMISSION_READ',       'View permissions',                          'PERMISSIONS', 'system'),
    ('PERMISSION_MANAGE',     'Create, update and delete permissions',     'PERMISSIONS', 'system'),
    ('SESSION_READ',          'View sessions of any user',                 'SESSIONS',    'system'),
    ('SESSION_MANAGE',        'Revoke sessions of any user',               'SESSIONS',    'system'),
    ('AUDIT_READ',            'Query audit logs',                          'AUDIT',       'system'),
    ('SYSTEM_SETTINGS',       'Manage system-wide settings',               'SYSTEM',      'system'),
    ('SERVICE_CLIENT_MANAGE', 'Manage machine-to-machine service clients', 'SYSTEM',      'system'),
    ('WORKSPACE_MANAGE',      'Suspend, reactivate and manage any workspace platform-wide', 'WORKSPACES', 'system'),
    ('NOTIFICATION_CATEGORY_READ',   'View notification categories',   'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_CATEGORY_CREATE', 'Create notification categories', 'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_CATEGORY_UPDATE', 'Update notification categories', 'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_CATEGORY_DELETE', 'Delete notification categories', 'NOTIFICATIONS', 'system'),
    -- Own resource, granted to USER by default.
    ('NOTIFICATION_READ',               'View your own notification inbox',       'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_UPDATE',             'Mark your own notifications as read',    'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_DELETE',             'Delete your own notifications',          'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_PREFERENCE_READ',    'View your own notification preferences', 'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_PREFERENCE_UPDATE',  'Update your own notification preferences', 'NOTIFICATIONS', 'system'),
    -- "_UNRESTRICTED" = not restricted to the caller's own data; stays privileged,
    -- not granted to USER below. Same suffix convention across every resource here.
    ('NOTIFICATION_READ_UNRESTRICTED',              'View any user''s notification inbox',       'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_PREFERENCE_READ_UNRESTRICTED',   'View any user''s notification preferences', 'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_PREFERENCE_UPDATE_UNRESTRICTED', 'Change any user''s notification preferences on their behalf', 'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_AUDIT_READ',                     'Read the notification audit trail',         'NOTIFICATIONS', 'system'),
    ('NOTIFICATION_DEAD_LETTER_REPLAY',             'Replay notification dead letter records',   'NOTIFICATIONS', 'system')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (name, description, is_system, created_by) VALUES
    ('ADMIN', 'Full unrestricted access',    TRUE, 'system'),
    ('USER',  'Standard authenticated user', TRUE, 'system')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Every standard user manages their own notifications; this is baseline
-- self-service, granted to USER the same way ADMIN gets everything above.
-- Every "_UNRESTRICTED" permission is deliberately excluded here; those stay privileged.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'USER'
  AND p.name IN ('NOTIFICATION_READ', 'NOTIFICATION_UPDATE', 'NOTIFICATION_DELETE',
                 'NOTIFICATION_PREFERENCE_READ', 'NOTIFICATION_PREFERENCE_UPDATE')
ON CONFLICT DO NOTHING;
