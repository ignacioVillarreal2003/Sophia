-- Per-person choices about their workspaces that the server has to honour on its own (the nightly clean-up cannot ask
-- the browser). Choices that only change how a page looks stay on the device.
CREATE TABLE user_workspace_preferences (
    user_id              UUID        PRIMARY KEY,
    trash_retention_days INTEGER     NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_workspace_prefs_days CHECK (trash_retention_days BETWEEN 1 AND 365)
);
