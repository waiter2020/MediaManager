-- PostgreSQL full-text search support for MediaManager.

CREATE TABLE media_fts (
    item_id        BIGINT PRIMARY KEY REFERENCES media_item(id) ON DELETE CASCADE,
    title          TEXT,
    original_title TEXT,
    overview       TEXT,
    file_names     TEXT,
    tags           TEXT,
    categories     TEXT,
    search_vector  TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(original_title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(overview, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(file_names, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(tags, '')), 'C') ||
        setweight(to_tsvector('simple', coalesce(categories, '')), 'C')
    ) STORED
);

CREATE INDEX idx_media_fts_vector ON media_fts USING GIN (search_vector);

