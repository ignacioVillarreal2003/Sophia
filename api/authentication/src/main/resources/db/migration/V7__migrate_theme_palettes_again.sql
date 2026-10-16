-- Replaces the VTRON/ZEN/DISCORD/ROSE_PINE/MINIMAL_FOREST/COSMIC_PURPLE
-- palettes with a new set of 14 (CLAUDE kept, 13 new ones added).
ALTER TABLE user_preferences DROP CONSTRAINT chk_user_preferences_theme;

UPDATE user_preferences SET theme = 'CLAUDE' WHERE theme <> 'CLAUDE';

ALTER TABLE user_preferences ADD CONSTRAINT chk_user_preferences_theme
    CHECK (theme IN ('CLAUDE', 'VIOLET_BLOOM', 'VERCEL', 'TWITTER', 'TANGERINE', 'SAGE_GARDEN',
                      'PASTEL_DREAMS', 'NOTEBOOK', 'NATURE', 'MODERN_MINIMAL', 'DARKMATTER',
                      'COSMIC_NIGHT', 'CLAYMORPHISM', 'CAFFEINE'));
