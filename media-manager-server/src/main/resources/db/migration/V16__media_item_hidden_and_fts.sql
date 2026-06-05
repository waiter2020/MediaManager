-- Soft-hide media items when all files are in recycle bin
ALTER TABLE media_item ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0;

-- Standalone FTS5 index (maintained by FtsIndexService)
CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
    item_id UNINDEXED,
    title,
    original_title,
    overview
);
