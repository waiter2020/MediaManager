ALTER TABLE media_collection ADD COLUMN smart INTEGER NOT NULL DEFAULT 0;
ALTER TABLE media_collection ADD COLUMN rule_json TEXT;

CREATE TABLE IF NOT EXISTS user_watchlist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    media_item_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    CONSTRAINT uk_user_watchlist_user_media UNIQUE (user_id, media_item_id),
    CONSTRAINT fk_user_watchlist_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_watchlist_media_item FOREIGN KEY (media_item_id) REFERENCES media_item(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_watchlist_user_created ON user_watchlist(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_watchlist_media_item ON user_watchlist(media_item_id);
