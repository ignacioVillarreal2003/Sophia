ALTER TABLE user_event_outbox
    ADD COLUMN locale         VARCHAR(10),
    ADD COLUMN correlation_id VARCHAR(64);

ALTER TABLE notification_command_outbox
    ADD COLUMN correlation_id VARCHAR(64);

UPDATE user_event_outbox o
SET locale = p.locale
FROM user_preferences p
WHERE p.user_id = o.user_id
  AND o.published_at IS NULL;

INSERT INTO user_event_outbox (event_type, user_id, email, first_name, last_name, locale, occurred_at)
SELECT 'USER_SYNC', u.id, u.email, u.first_name, u.last_name, p.locale, now()
FROM users u
         LEFT JOIN user_preferences p ON p.user_id = u.id
WHERE u.deleted_at IS NULL;
