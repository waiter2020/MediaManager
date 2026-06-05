-- Extend scrape_task to support schedule-driven tasks and parameter snapshots
ALTER TABLE scrape_task ADD COLUMN schedule_id BIGINT;
ALTER TABLE scrape_task ADD COLUMN target_status VARCHAR(16) NOT NULL DEFAULT 'UNIDENTIFIED';
ALTER TABLE scrape_task ADD COLUMN media_types TEXT;
ALTER TABLE scrape_task ADD COLUMN params_json TEXT;

CREATE INDEX IF NOT EXISTS idx_scrape_task_schedule ON scrape_task(schedule_id);
