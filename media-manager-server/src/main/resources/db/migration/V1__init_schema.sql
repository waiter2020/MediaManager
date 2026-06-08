-- PostgreSQL baseline schema (no data migration from SQLite).

-- 1. Users / roles / permissions
CREATE TABLE sys_user (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128),
    email         VARCHAR(255),
    avatar_path   VARCHAR(512),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE TABLE sys_role (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(32) UNIQUE NOT NULL,
    name        VARCHAR(64) NOT NULL,
    description TEXT,
    built_in    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_permission (
    id         SERIAL PRIMARY KEY,
    code       VARCHAR(64) UNIQUE NOT NULL,
    name       VARCHAR(128) NOT NULL,
    group_name VARCHAR(32)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_refresh_token (
    id         SERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    token      VARCHAR(512) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. System config
CREATE TABLE sys_config (
    id           SERIAL PRIMARY KEY,
    config_key   VARCHAR(128) UNIQUE NOT NULL,
    config_value TEXT,
    description  VARCHAR(256)
);

-- 3. Libraries
CREATE TABLE media_library (
    id                    SERIAL PRIMARY KEY,
    name                  VARCHAR(128) NOT NULL,
    type                  VARCHAR(16) NOT NULL,
    language              VARCHAR(8) DEFAULT 'zh',
    auto_scan             BOOLEAN NOT NULL DEFAULT TRUE,
    scan_interval_minutes INT DEFAULT 30,
    last_scanned_at       TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP
);

CREATE TABLE library_access (
    id              SERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    library_id      BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    can_view        BOOLEAN NOT NULL DEFAULT TRUE,
    can_edit        BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete_file BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE library_path (
    id         SERIAL PRIMARY KEY,
    library_id BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    path       VARCHAR(1024) NOT NULL,
    priority   INT DEFAULT 0
);

CREATE TABLE library_extractor_config (
    id             SERIAL PRIMARY KEY,
    library_id      BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    extractor_type  VARCHAR(32) NOT NULL,
    priority        INT DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    config          TEXT
);

CREATE TABLE library_plugin_config (
    id         SERIAL PRIMARY KEY,
    library_id BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    plugin_id  VARCHAR(64) NOT NULL,
    kind       VARCHAR(32) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    priority   INT NOT NULL DEFAULT 100,
    config     TEXT,
    UNIQUE (library_id, plugin_id, kind)
);

-- 4. Media
CREATE TABLE media_item (
    id            SERIAL PRIMARY KEY,
    library_id    BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    title         VARCHAR(512),
    original_title VARCHAR(512),
    sort_title    VARCHAR(512),
    type          VARCHAR(16) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'UNIDENTIFIED',
    release_date  DATE,
    rating        DECIMAL(3, 1),
    overview      TEXT,
    poster_path   VARCHAR(1024),
    backdrop_path VARCHAR(1024),
    provider_ids  TEXT,
    custom_fields TEXT,
    hidden        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,
    last_scanned_at TIMESTAMP
);

CREATE INDEX idx_media_item_lib_type_status ON media_item(library_id, type, status);
CREATE INDEX idx_media_item_type_date ON media_item(type, release_date);
CREATE INDEX idx_media_item_library_hidden_id ON media_item(library_id, hidden, id);

CREATE TABLE media_file (
    id               SERIAL PRIMARY KEY,
    media_item_id    BIGINT REFERENCES media_item(id) ON DELETE SET NULL,
    file_path        VARCHAR(2048) UNIQUE NOT NULL,
    file_name        VARCHAR(512),
    file_size        BIGINT,
    mime_type        VARCHAR(128),
    container        VARCHAR(16),
    video_codec      VARCHAR(32),
    audio_codec      VARCHAR(32),
    width            INT,
    height           INT,
    duration_seconds INT,
    bitrate          INT,
    checksum_sha256  VARCHAR(64),
    file_modified_at TIMESTAMP,
    deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_file_item_id ON media_file(media_item_id);

CREATE TABLE media_subtitle (
    id             SERIAL PRIMARY KEY,
    media_item_id  BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    media_file_id  BIGINT REFERENCES media_file(id) ON DELETE CASCADE,
    file_path      VARCHAR(2048) NOT NULL UNIQUE,
    file_name      VARCHAR(512),
    language       VARCHAR(32),
    format         VARCHAR(16),
    title          VARCHAR(256),
    source         VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    provider       VARCHAR(64),
    external_id    VARCHAR(128),
    file_size      BIGINT,
    file_modified_at TIMESTAMP,
    default_track  BOOLEAN NOT NULL DEFAULT FALSE,
    forced         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE INDEX idx_media_subtitle_item_id ON media_subtitle(media_item_id);
CREATE INDEX idx_media_subtitle_file_id ON media_subtitle(media_file_id);
CREATE INDEX idx_media_subtitle_language ON media_subtitle(language);

CREATE TABLE media_chapter (
    id             SERIAL PRIMARY KEY,
    media_file_id  BIGINT NOT NULL REFERENCES media_file(id) ON DELETE CASCADE,
    chapter_index  INT NOT NULL,
    title          VARCHAR(256),
    start_seconds  DOUBLE PRECISION NOT NULL,
    end_seconds    DOUBLE PRECISION,
    source         VARCHAR(32) NOT NULL DEFAULT 'EMBEDDED',
    thumbnail_path VARCHAR(2048),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP,
    UNIQUE (media_file_id, chapter_index)
);

CREATE INDEX idx_media_chapter_file_id ON media_chapter(media_file_id);
CREATE INDEX idx_media_chapter_start ON media_chapter(media_file_id, start_seconds);

-- 5. Metadata
CREATE TABLE movie_metadata (
    id             SERIAL PRIMARY KEY,
    media_item_id  BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    tagline        VARCHAR(512),
    runtime_minutes INT,
    certification  VARCHAR(16),
    genres         TEXT,
    studios        TEXT,
    cast_info      TEXT,
    crew           TEXT,
    trailer_url    VARCHAR(1024)
);

CREATE TABLE tv_show_metadata (
    id             SERIAL PRIMARY KEY,
    media_item_id  BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    status         VARCHAR(16),
    network        VARCHAR(128),
    genres         TEXT,
    cast_info      TEXT
);

CREATE TABLE season (
    id                 SERIAL PRIMARY KEY,
    tv_show_metadata_id BIGINT NOT NULL REFERENCES tv_show_metadata(id) ON DELETE CASCADE,
    season_number       INT,
    name                VARCHAR(256),
    overview            TEXT,
    poster_path         VARCHAR(1024)
);

CREATE TABLE episode (
    id            SERIAL PRIMARY KEY,
    season_id     BIGINT NOT NULL REFERENCES season(id) ON DELETE CASCADE,
    media_file_id BIGINT UNIQUE REFERENCES media_file(id) ON DELETE SET NULL,
    episode_number INT,
    title          VARCHAR(512),
    overview       TEXT,
    air_date       DATE,
    runtime_minutes INT,
    rating         DECIMAL(3, 1)
);

CREATE TABLE image_metadata (
    id            SERIAL PRIMARY KEY,
    media_item_id BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    width         INT,
    height        INT,
    camera_make   VARCHAR(128),
    camera_model  VARCHAR(128),
    lens          VARCHAR(128),
    iso           VARCHAR(32),
    aperture      VARCHAR(32),
    shutter_speed VARCHAR(32),
    taken_at      TIMESTAMP,
    gps_latitude  DOUBLE PRECISION,
    gps_longitude DOUBLE PRECISION,
    exif_data     TEXT
);

CREATE TABLE audio_metadata (
    id            SERIAL PRIMARY KEY,
    media_item_id BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    artist        VARCHAR(256),
    album         VARCHAR(256),
    album_artist  VARCHAR(256),
    track_number  INT,
    disc_number   INT,
    genres        TEXT,
    duration_seconds INT,
    bitrate       INT,
    sample_rate   INT,
    channels      INT
);

-- 6. Tags / categories / rules
CREATE TABLE tag (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(64) UNIQUE NOT NULL,
    color      VARCHAR(7),
    source     VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE category (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    parent_id  BIGINT REFERENCES category(id) ON DELETE CASCADE,
    type       VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE media_item_tag (
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    tag_id        BIGINT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (media_item_id, tag_id)
);

CREATE INDEX idx_media_item_tag_tag_id ON media_item_tag(tag_id);

CREATE TABLE media_item_category (
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    category_id   BIGINT NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    PRIMARY KEY (media_item_id, category_id)
);

CREATE TABLE classification_rule (
    id           SERIAL PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    rule_type    VARCHAR(32) NOT NULL,
    expression   TEXT NOT NULL,
    target_type  VARCHAR(32) NOT NULL,
    target_value VARCHAR(256) NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    priority     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. User activity (epoch millis)
CREATE TABLE user_playback_history (
    id              SERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    media_item_id   BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    played_at       BIGINT NOT NULL,
    position        INT DEFAULT 0,
    duration_seconds INT,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at    BIGINT,
    play_count      INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_playback_user_time ON user_playback_history(user_id, played_at DESC);
CREATE UNIQUE INDEX idx_playback_user_item ON user_playback_history(user_id, media_item_id);
CREATE INDEX idx_playback_user_completed ON user_playback_history(user_id, completed, played_at DESC);

CREATE TABLE user_favorite (
    id            SERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    created_at    BIGINT NOT NULL
);

CREATE INDEX idx_favorite_user_time ON user_favorite(user_id, created_at DESC);
CREATE UNIQUE INDEX idx_favorite_user_item ON user_favorite(user_id, media_item_id);

CREATE TABLE user_watchlist (
    id            SERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    created_at    BIGINT NOT NULL,
    UNIQUE (user_id, media_item_id)
);

CREATE INDEX idx_user_watchlist_user_created ON user_watchlist(user_id, created_at);
CREATE INDEX idx_user_watchlist_media_item ON user_watchlist(media_item_id);

-- 8. Collections (epoch millis)
CREATE TABLE media_collection (
    id            SERIAL PRIMARY KEY,
    owner_user_id BIGINT REFERENCES sys_user(id) ON DELETE SET NULL,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    type          VARCHAR(16) NOT NULL DEFAULT 'COLLECTION',
    visibility    VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    poster_path   VARCHAR(1024),
    smart         BOOLEAN NOT NULL DEFAULT FALSE,
    rule_json     TEXT,
    created_at    BIGINT NOT NULL,
    updated_at    BIGINT
);

CREATE INDEX idx_collection_owner ON media_collection(owner_user_id, created_at DESC);
CREATE INDEX idx_collection_visibility ON media_collection(visibility, created_at DESC);

CREATE TABLE media_collection_item (
    id            SERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES media_collection(id) ON DELETE CASCADE,
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    position      INT NOT NULL DEFAULT 0,
    created_at    BIGINT NOT NULL,
    UNIQUE (collection_id, media_item_id)
);

CREATE INDEX idx_collection_item_collection ON media_collection_item(collection_id, position ASC, created_at ASC);
CREATE INDEX idx_collection_item_media ON media_collection_item(media_item_id);

-- 9. Scrape tasks and schedules
CREATE TABLE scrape_task (
    id            SERIAL PRIMARY KEY,
    library_id    BIGINT REFERENCES media_library(id) ON DELETE SET NULL,
    schedule_id   BIGINT,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    trigger_type  VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    target_status VARCHAR(16) NOT NULL DEFAULT 'UNIDENTIFIED',
    media_types   TEXT,
    params_json   TEXT,
    total_items   INT DEFAULT 0,
    scraped_items INT DEFAULT 0,
    error_items   INT DEFAULT 0,
    error_log     TEXT,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scrape_task_status ON scrape_task(status);
CREATE INDEX idx_scrape_task_library ON scrape_task(library_id);
CREATE INDEX idx_scrape_task_schedule ON scrape_task(schedule_id);

CREATE TABLE scrape_schedule (
    id                      SERIAL PRIMARY KEY,
    name                    VARCHAR(128) NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    schedule_type           VARCHAR(16) NOT NULL,
    cron_expr               VARCHAR(128),
    interval_seconds        INT,
    scope                   VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
    library_id              BIGINT REFERENCES media_library(id) ON DELETE SET NULL,
    target_status           VARCHAR(16) NOT NULL DEFAULT 'UNIDENTIFIED',
    media_types             TEXT,
    max_concurrency         INT NOT NULL DEFAULT 1,
    batch_size_override     INT,
    request_delay_ms_override INT,
    next_run_at             TIMESTAMP,
    last_run_at             TIMESTAMP,
    last_status             VARCHAR(16),
    last_error              TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP
);

CREATE INDEX idx_scrape_schedule_enabled_next_run ON scrape_schedule(enabled, next_run_at);
CREATE INDEX idx_scrape_schedule_library ON scrape_schedule(library_id);

-- 10. AI features
CREATE TABLE ai_suggestion (
    id             SERIAL PRIMARY KEY,
    media_item_id  BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    field_name     VARCHAR(64) NOT NULL,
    suggested_value TEXT,
    provider_id    VARCHAR(64),
    confidence     REAL,
    review_status  VARCHAR(16) DEFAULT 'PENDING',
    reviewed_by    INT,
    reviewed_at    TIMESTAMP,
    created_at     TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_suggestion_status ON ai_suggestion(review_status);
CREATE INDEX idx_ai_suggestion_item_field_value_status ON ai_suggestion(media_item_id, field_name, suggested_value, review_status);

CREATE TABLE media_embedding (
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    model_id      VARCHAR(64) NOT NULL,
    vector        BYTEA NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (media_item_id, model_id)
);

