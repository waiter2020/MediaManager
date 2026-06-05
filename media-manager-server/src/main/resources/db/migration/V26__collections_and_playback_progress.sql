-- Plex/Jellyfin style collections plus richer playback progress.

ALTER TABLE user_playback_history ADD COLUMN duration_seconds INTEGER;
ALTER TABLE user_playback_history ADD COLUMN completed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_playback_history ADD COLUMN completed_at INTEGER;
ALTER TABLE user_playback_history ADD COLUMN play_count INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_playback_user_completed
    ON user_playback_history(user_id, completed, played_at DESC);

CREATE TABLE IF NOT EXISTS media_collection (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_user_id INTEGER REFERENCES sys_user(id) ON DELETE SET NULL,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    type          VARCHAR(16) NOT NULL DEFAULT 'COLLECTION',
    visibility    VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    poster_path   VARCHAR(1024),
    created_at    INTEGER NOT NULL DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000),
    updated_at    INTEGER
);

CREATE INDEX IF NOT EXISTS idx_collection_owner
    ON media_collection(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_collection_visibility
    ON media_collection(visibility, created_at DESC);

CREATE TABLE IF NOT EXISTS media_collection_item (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    collection_id INTEGER NOT NULL REFERENCES media_collection(id) ON DELETE CASCADE,
    media_item_id INTEGER NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    position      INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL DEFAULT (CAST(strftime('%s', 'now') AS INTEGER) * 1000),
    UNIQUE(collection_id, media_item_id)
);

CREATE INDEX IF NOT EXISTS idx_collection_item_collection
    ON media_collection_item(collection_id, position ASC, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_collection_item_media
    ON media_collection_item(media_item_id);
