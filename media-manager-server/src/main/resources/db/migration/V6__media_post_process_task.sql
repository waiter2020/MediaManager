CREATE TABLE media_post_process_task (
    id              SERIAL PRIMARY KEY,
    task_type       VARCHAR(32) NOT NULL,
    media_item_id   BIGINT REFERENCES media_item(id) ON DELETE CASCADE,
    media_file_id   BIGINT REFERENCES media_file(id) ON DELETE CASCADE,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message   TEXT,
    source          VARCHAR(32),
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_post_process_target CHECK (
        (task_type = 'ITEM_FULL' AND media_item_id IS NOT NULL AND media_file_id IS NULL)
        OR (task_type = 'FILE_CHAPTERS' AND media_file_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_post_process_active_item
    ON media_post_process_task (task_type, media_item_id)
    WHERE status IN ('PENDING', 'RUNNING') AND media_item_id IS NOT NULL;

CREATE UNIQUE INDEX uq_post_process_active_file
    ON media_post_process_task (task_type, media_file_id)
    WHERE status IN ('PENDING', 'RUNNING') AND media_file_id IS NOT NULL;

CREATE INDEX idx_post_process_claim
    ON media_post_process_task (status, next_retry_at, created_at);
