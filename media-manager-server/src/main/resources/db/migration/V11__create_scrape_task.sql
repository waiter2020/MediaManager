-- Scrape task tracking table
CREATE TABLE scrape_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    library_id BIGINT REFERENCES media_library(id) ON DELETE SET NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    total_items INT DEFAULT 0,
    scraped_items INT DEFAULT 0,
    error_items INT DEFAULT 0,
    error_log TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_scrape_task_status ON scrape_task(status);
CREATE INDEX idx_scrape_task_library ON scrape_task(library_id);
