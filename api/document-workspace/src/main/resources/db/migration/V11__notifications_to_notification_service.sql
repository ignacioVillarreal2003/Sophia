-- V11: In-app notification storage moves to the notification microservice.
-- The outbox gains a per-row destination topic so notification commands can be
-- relayed to the notification service's Kafka topic alongside domain events.

ALTER TABLE outbox_events ADD COLUMN topic VARCHAR(255);
ALTER TABLE outbox_events ALTER COLUMN aggregate_id TYPE VARCHAR(255);

DROP TABLE notifications;
