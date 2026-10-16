-- Replaces the old light/dark/system preference with a single app-wide
-- visual theme concept. Only 'AURORA' exists today; more values get added
-- here (and to the CHECK constraint) as more themes are built.
ALTER TABLE user_preferences DROP CONSTRAINT chk_user_preferences_theme;

UPDATE user_preferences SET theme = 'AURORA' WHERE theme IN ('LIGHT', 'DARK', 'SYSTEM');

ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'AURORA';
ALTER TABLE user_preferences ADD CONSTRAINT chk_user_preferences_theme CHECK (theme IN ('AURORA'));
