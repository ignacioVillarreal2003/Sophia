-- Replaces the ARCTIC_DEPTHS/AURORA_BLOSSOM/JADE_HORIZON/ROSE_SUNRISE palettes
-- with the current design-system palettes, and reintroduces light/dark as an
-- independent axis (dropped back in V3) alongside the palette choice.
ALTER TABLE user_preferences DROP CONSTRAINT chk_user_preferences_theme;

UPDATE user_preferences SET theme = 'CLAUDE';

ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'CLAUDE';
ALTER TABLE user_preferences ADD CONSTRAINT chk_user_preferences_theme
    CHECK (theme IN ('CLAUDE', 'VTRON', 'ZEN', 'DISCORD', 'ROSE_PINE', 'MINIMAL_FOREST', 'COSMIC_PURPLE'));

ALTER TABLE user_preferences ADD COLUMN theme_mode VARCHAR(10) NOT NULL DEFAULT 'LIGHT';
ALTER TABLE user_preferences ADD CONSTRAINT chk_user_preferences_theme_mode CHECK (theme_mode IN ('LIGHT', 'DARK'));
