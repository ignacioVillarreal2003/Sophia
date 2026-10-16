-- Replaces the single AURORA theme with a set of selectable color palettes:
-- ARCTIC_DEPTHS, AURORA_BLOSSOM, JADE_HORIZON, ROSE_SUNRISE. All share the
-- same animated backdrop + glass surfaces mechanic — only the colors differ.
ALTER TABLE user_preferences DROP CONSTRAINT chk_user_preferences_theme;

UPDATE user_preferences SET theme = 'ARCTIC_DEPTHS' WHERE theme = 'AURORA';

ALTER TABLE user_preferences ALTER COLUMN theme SET DEFAULT 'ARCTIC_DEPTHS';
ALTER TABLE user_preferences ADD CONSTRAINT chk_user_preferences_theme
    CHECK (theme IN ('ARCTIC_DEPTHS', 'AURORA_BLOSSOM', 'JADE_HORIZON', 'ROSE_SUNRISE'));
