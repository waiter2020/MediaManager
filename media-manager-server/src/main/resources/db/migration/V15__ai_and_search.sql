CREATE TABLE IF NOT EXISTS ai_suggestion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id INTEGER NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    field_name VARCHAR(64) NOT NULL,
    suggested_value TEXT,
    provider_id VARCHAR(64),
    confidence REAL,
    review_status VARCHAR(16) DEFAULT 'PENDING',
    reviewed_by INTEGER,
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_suggestion_status ON ai_suggestion(review_status);

CREATE TABLE IF NOT EXISTS media_embedding (
    media_item_id INTEGER NOT NULL,
    model_id VARCHAR(64) NOT NULL,
    vector BLOB NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (media_item_id, model_id)
);
