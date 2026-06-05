CREATE INDEX IF NOT EXISTS idx_media_item_tag_tag_id
    ON media_item_tag(tag_id);

CREATE INDEX IF NOT EXISTS idx_media_item_library_hidden_id
    ON media_item(library_id, hidden, id);

CREATE INDEX IF NOT EXISTS idx_ai_suggestion_item_field_value_status
    ON ai_suggestion(media_item_id, field_name, suggested_value, review_status);
