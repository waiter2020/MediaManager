CREATE INDEX IF NOT EXISTS idx_media_item_library_id_hidden
    ON media_item(library_id, id, hidden);
