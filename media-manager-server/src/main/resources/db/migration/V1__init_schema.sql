-- 1. 用户与权限表
CREATE TABLE sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128),
    email VARCHAR(255),
    avatar_path VARCHAR(512),
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE sys_role (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    built_in INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE sys_permission (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    group_name VARCHAR(32)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_refresh_token (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    token VARCHAR(512) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. 媒体库表 (Must compile before library_access referenced FK)
CREATE TABLE media_library (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL,
    language VARCHAR(8) DEFAULT 'zh',
    auto_scan INTEGER NOT NULL DEFAULT 1,
    scan_interval_minutes INT DEFAULT 30,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE library_access (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    library_id BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    can_view INTEGER NOT NULL DEFAULT 1,
    can_edit INTEGER NOT NULL DEFAULT 0,
    can_delete_file INTEGER NOT NULL DEFAULT 0
);

-- 2. 系统配置表
CREATE TABLE sys_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key VARCHAR(128) UNIQUE NOT NULL,
    config_value TEXT,
    description VARCHAR(256)
);

CREATE TABLE library_path (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    library_id BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    path VARCHAR(1024) NOT NULL,
    priority INT DEFAULT 0
);

CREATE TABLE library_extractor_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    library_id BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    extractor_type VARCHAR(32) NOT NULL,
    priority INT DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    config TEXT
);

-- 4. 媒体项表
CREATE TABLE media_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    library_id BIGINT NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
    title VARCHAR(512),
    original_title VARCHAR(512),
    sort_title VARCHAR(512),
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNIDENTIFIED',
    release_date DATE,
    rating DECIMAL(3,1),
    overview TEXT,
    poster_path VARCHAR(1024),
    backdrop_path VARCHAR(1024),
    provider_ids TEXT,
    custom_fields TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    last_scanned_at TIMESTAMP
);

CREATE INDEX idx_media_item_lib_type_status ON media_item(library_id, type, status);
CREATE INDEX idx_media_item_type_date ON media_item(type, release_date);

CREATE TABLE media_file (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id BIGINT REFERENCES media_item(id) ON DELETE SET NULL,
    file_path VARCHAR(2048) UNIQUE NOT NULL,
    file_name VARCHAR(512),
    file_size BIGINT,
    mime_type VARCHAR(128),
    container VARCHAR(16),
    video_codec VARCHAR(32),
    audio_codec VARCHAR(32),
    width INT,
    height INT,
    duration_seconds INT,
    bitrate INT,
    checksum_sha256 VARCHAR(64),
    file_modified_at TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_media_file_item_id ON media_file(media_item_id);

-- 5. 元数据表
CREATE TABLE movie_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    tagline VARCHAR(512),
    runtime_minutes INT,
    certification VARCHAR(16),
    genres TEXT,
    studios TEXT,
    cast_info TEXT,
    crew TEXT,
    trailer_url VARCHAR(1024)
);

CREATE TABLE tv_show_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    status VARCHAR(16),
    network VARCHAR(128),
    genres TEXT,
    cast_info TEXT
);

CREATE TABLE season (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tv_show_metadata_id BIGINT NOT NULL REFERENCES tv_show_metadata(id) ON DELETE CASCADE,
    season_number INT,
    name VARCHAR(256),
    overview TEXT,
    poster_path VARCHAR(1024)
);

CREATE TABLE episode (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    season_id BIGINT NOT NULL REFERENCES season(id) ON DELETE CASCADE,
    media_file_id BIGINT UNIQUE REFERENCES media_file(id) ON DELETE SET NULL,
    episode_number INT,
    title VARCHAR(512),
    overview TEXT,
    air_date DATE,
    runtime_minutes INT,
    rating DECIMAL(3,1)
);

CREATE TABLE image_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    width INT,
    height INT,
    camera_make VARCHAR(128),
    camera_model VARCHAR(128),
    lens VARCHAR(128),
    iso VARCHAR(32),
    aperture VARCHAR(32),
    shutter_speed VARCHAR(32),
    taken_at TIMESTAMP,
    gps_latitude DOUBLE PRECISION,
    gps_longitude DOUBLE PRECISION,
    exif_data TEXT
);

CREATE TABLE audio_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_item_id BIGINT UNIQUE NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    artist VARCHAR(256),
    album VARCHAR(256),
    album_artist VARCHAR(256),
    track_number INT,
    disc_number INT,
    genres TEXT,
    duration_seconds INT,
    bitrate INT,
    sample_rate INT,
    channels INT
);

-- 6. 分类与标签
CREATE TABLE tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(64) UNIQUE NOT NULL,
    color VARCHAR(7),
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT REFERENCES category(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE media_item_tag (
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (media_item_id, tag_id)
);

CREATE TABLE media_item_category (
    media_item_id BIGINT NOT NULL REFERENCES media_item(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    PRIMARY KEY (media_item_id, category_id)
);
