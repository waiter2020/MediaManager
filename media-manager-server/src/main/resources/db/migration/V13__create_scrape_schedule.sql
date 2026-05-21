-- Schedule rules for metadata scraping (frontend-configurable)
CREATE TABLE scrape_schedule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT 1,

    schedule_type VARCHAR(16) NOT NULL, -- CRON | FIXED_DELAY
    cron_expr VARCHAR(128),
    interval_seconds INT,

    scope VARCHAR(16) NOT NULL DEFAULT 'GLOBAL', -- GLOBAL | LIBRARY
    library_id BIGINT REFERENCES media_library(id) ON DELETE SET NULL,

    target_status VARCHAR(16) NOT NULL DEFAULT 'UNIDENTIFIED', -- UNIDENTIFIED | IDENTIFIED | ALL
    media_types TEXT, -- JSON array string, e.g. ["MOVIE","TV_SHOW"]

    max_concurrency INT NOT NULL DEFAULT 1,
    batch_size_override INT,
    request_delay_ms_override INT,

    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_status VARCHAR(16),
    last_error TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_scrape_schedule_enabled_next_run ON scrape_schedule(enabled, next_run_at);
CREATE INDEX idx_scrape_schedule_library ON scrape_schedule(library_id);
