CREATE TABLE IF NOT EXISTS library_plugin_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    library_id INTEGER NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    plugin_id VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    enabled BOOLEAN DEFAULT 1,
    priority INTEGER DEFAULT 100,
    config TEXT,
    UNIQUE(library_id, plugin_id, kind)
);

INSERT OR IGNORE INTO library_plugin_config (library_id, plugin_id, kind, enabled, priority, config)
SELECT library_id, extractor_type, 'EXTRACTOR', enabled, priority, config
FROM library_extractor_config;
