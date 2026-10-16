INSERT INTO users
    (id, first_name, last_name, email, password_hash, active, email_verified, auth_provider,
     created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-00000000000f', 'Demo', 'Owner', 'demo@sophia.local',
     '$2b$10$AGi9ZY552dmYU3uicbBkXODwaUFzfiL.8/KcHsRzOmHJBN2S.n/9m', TRUE, TRUE, 'LOCAL', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_preferences (user_id, locale, timezone, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-00000000000f', 'en-US', 'America/Montevideo', NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT 'a0000000-0000-0000-0000-00000000000f', r.id FROM roles r WHERE r.name = 'USER'
ON CONFLICT DO NOTHING;
