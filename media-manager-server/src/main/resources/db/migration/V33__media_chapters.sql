CREATE TABLE media_chapter (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_file_id BIGINT NOT NULL REFERENCES media_file(id) ON DELETE CASCADE,
    chapter_index INT NOT NULL,
    title VARCHAR(256),
    start_seconds DOUBLE PRECISION NOT NULL,
    end_seconds DOUBLE PRECISION,
    source VARCHAR(32) NOT NULL DEFAULT 'EMBEDDED',
    thumbnail_path VARCHAR(2048),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(media_file_id, chapter_index)
);

CREATE INDEX idx_media_chapter_file_id ON media_chapter(media_file_id);
CREATE INDEX idx_media_chapter_start ON media_chapter(media_file_id, start_seconds);
