-- Modules the user has taken off the home screen. A deny list, not an allow
-- list: an empty array means "show everything", so a module shipped after the
-- row was written appears for existing users without a backfill.
ALTER TABLE user_preferences
    ADD COLUMN hidden_modules JSONB NOT NULL DEFAULT '[]'::jsonb;
