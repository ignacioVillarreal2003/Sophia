INSERT INTO user_event_outbox (event_type, user_id, email, first_name, last_name, locale, timezone, occurred_at)
SELECT 'USER_SYNC', u.id, u.email, u.first_name, u.last_name, p.locale, p.timezone, now()
FROM users u
LEFT JOIN user_preferences p ON p.user_id = u.id
WHERE u.deleted_at IS NULL;
