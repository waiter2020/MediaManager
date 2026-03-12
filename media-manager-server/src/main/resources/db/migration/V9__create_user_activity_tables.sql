-- User playback history
CREATE TABLE IF NOT EXISTS user_playback_history (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL,
    media_item_id INTEGER NOT NULL,
    played_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    position      INTEGER DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id) REFERENCES media_item(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_playback_user_time ON user_playback_history(user_id, played_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_playback_user_item ON user_playback_history(user_id, media_item_id);

-- User favorites
CREATE TABLE IF NOT EXISTS user_favorite (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL,
    media_item_id INTEGER NOT NULL,
    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id) REFERENCES media_item(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_favorite_user_time ON user_favorite(user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_favorite_user_item ON user_favorite(user_id, media_item_id);
