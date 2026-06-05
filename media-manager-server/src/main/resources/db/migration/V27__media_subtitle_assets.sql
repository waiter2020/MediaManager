CREATE TABLE media_subtitle (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    media_file_id BIGINT REFERENCES media_file(id) ON DELETE CASCADE,
    file_path VARCHAR(2048) NOT NULL UNIQUE,
    file_name VARCHAR(512),
    language VARCHAR(32),
    format VARCHAR(16),
    title VARCHAR(256),
    source VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    provider VARCHAR(64),
    external_id VARCHAR(128),
    file_size BIGINT,
    file_modified_at TIMESTAMP,
    default_track INTEGER NOT NULL DEFAULT 0,
    forced INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_media_subtitle_item_id ON media_subtitle(media_item_id);
CREATE INDEX idx_media_subtitle_file_id ON media_subtitle(media_file_id);
CREATE INDEX idx_media_subtitle_language ON media_subtitle(language);
