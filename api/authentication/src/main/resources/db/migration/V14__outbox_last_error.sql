ALTER TABLE user_event_outbox
    ADD COLUMN last_error VARCHAR(2000);

ALTER TABLE notification_command_outbox
    ADD COLUMN last_error VARCHAR(2000);
