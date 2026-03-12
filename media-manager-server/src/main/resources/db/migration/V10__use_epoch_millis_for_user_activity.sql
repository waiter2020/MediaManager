-- Migrate user activity timestamps to INTEGER epoch milliseconds

-- 1. Add temporary INTEGER columns to hold epoch millis
ALTER TABLE user_playback_history ADD COLUMN played_at_ms INTEGER;
ALTER TABLE user_favorite        ADD COLUMN created_at_ms INTEGER;

-- 2. Populate new columns for rows where timestamps are stored as 13-digit millis strings
UPDATE user_playback_history
SET played_at_ms = CAST(played_at AS INTEGER)
WHERE played_at GLOB '[0-9]*'
  AND length(played_at) = 13;

UPDATE user_favorite
SET created_at_ms = CAST(created_at AS INTEGER)
WHERE created_at GLOB '[0-9]*'
  AND length(created_at) = 13;

-- 3. Populate remaining rows assuming they are textual datetime values
--    Use strftime('%s', ...) to get epoch seconds, then multiply by 1000 for millis
UPDATE user_playback_history
SET played_at_ms = CAST(strftime('%s', played_at) AS INTEGER) * 1000
WHERE played_at_ms IS NULL
  AND played_at IS NOT NULL;

UPDATE user_favorite
SET created_at_ms = CAST(strftime('%s', created_at) AS INTEGER) * 1000
WHERE created_at_ms IS NULL
  AND created_at IS NOT NULL;

-- 4. Rebuild user_playback_history table with INTEGER played_at
CREATE TABLE user_playback_history_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL,
    media_item_id INTEGER NOT NULL,
    played_at     INTEGER NOT NULL,
    position      INTEGER DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id) REFERENCES media_item(id) ON DELETE CASCADE
);

INSERT INTO user_playback_history_new (id, user_id, media_item_id, played_at, position)
SELECT id, user_id, media_item_id, played_at_ms, position
FROM user_playback_history;

DROP TABLE user_playback_history;

ALTER TABLE user_playback_history_new RENAME TO user_playback_history;

CREATE INDEX IF NOT EXISTS idx_playback_user_time ON user_playback_history(user_id, played_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_playback_user_item ON user_playback_history(user_id, media_item_id);

-- 5. Rebuild user_favorite table with INTEGER created_at
CREATE TABLE user_favorite_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL,
    media_item_id INTEGER NOT NULL,
    created_at    INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (media_item_id) REFERENCES media_item(id) ON DELETE CASCADE
);

INSERT INTO user_favorite_new (id, user_id, media_item_id, created_at)
SELECT id, user_id, media_item_id, created_at_ms
FROM user_favorite;

DROP TABLE user_favorite;

ALTER TABLE user_favorite_new RENAME TO user_favorite;

CREATE INDEX IF NOT EXISTS idx_favorite_user_time ON user_favorite(user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_favorite_user_item ON user_favorite(user_id, media_item_id);

