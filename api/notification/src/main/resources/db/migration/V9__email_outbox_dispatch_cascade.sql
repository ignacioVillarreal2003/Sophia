-- notification_dispatches.notification_id cascades from notifications on delete,
-- but email_outbox.dispatch_id did not cascade from notification_dispatches:
-- deleting a notification with a dispatched email violated this foreign key.
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_dispatch_id_fkey;
ALTER TABLE email_outbox
    ADD CONSTRAINT email_outbox_dispatch_id_fkey
        FOREIGN KEY (dispatch_id) REFERENCES notification_dispatches (id) ON DELETE CASCADE;
