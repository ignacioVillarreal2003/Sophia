INSERT INTO user_contacts (user_id, email, first_name, last_name, locale, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-00000000000f', 'demo@sophia.local', 'Demo', 'Owner', 'en-US', NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;
