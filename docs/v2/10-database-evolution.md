# MediaManager v2 — 数据库演进

> 基线 schema 见 `docs/02-database-schema.md`。本文描述 v2 增量迁移。

## 1. 迁移原则

- Flyway 顺序脚本 `V{n}__*.sql`
- 只增不删列（弃用标记 deprecated）
- SQLite 兼容语法

## 2. Phase 1 迁移

### V14__library_access_enforce_index.sql（可选）

```sql
CREATE INDEX IF NOT EXISTS idx_library_access_user ON library_access(user_id);
CREATE INDEX IF NOT EXISTS idx_library_access_library ON library_access(library_id);
```

### V15__media_file_recycle.sql

```sql
-- deleted_at 若不存在则添加
ALTER TABLE media_file ADD COLUMN deleted_at INTEGER;
CREATE INDEX IF NOT EXISTS idx_media_file_deleted ON media_file(deleted, deleted_at);
```

## 3. Phase 2 迁移

### V16__library_plugin_config.sql

```sql
CREATE TABLE library_plugin_config (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  library_id INTEGER NOT NULL REFERENCES media_library(id) ON DELETE CASCADE,
  plugin_id VARCHAR(64) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  enabled BOOLEAN DEFAULT 1,
  priority INTEGER DEFAULT 100,
  config TEXT,
  UNIQUE(library_id, plugin_id, kind)
);

-- 从 library_extractor_config 迁移
INSERT INTO library_plugin_config (library_id, plugin_id, kind, enabled, priority, config)
SELECT library_id, extractor_type, 'EXTRACTOR', enabled, priority, config
FROM library_extractor_config;
```

## 4. Phase 3 迁移

### V17__ai_suggestion.sql

```sql
CREATE TABLE ai_suggestion (
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
CREATE INDEX idx_ai_suggestion_status ON ai_suggestion(review_status);
```

### V18__media_embedding.sql

```sql
CREATE TABLE media_embedding (
  media_item_id INTEGER NOT NULL,
  model_id VARCHAR(64) NOT NULL,
  vector BLOB NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (media_item_id, model_id)
);
```

### V19__media_fts.sql

```sql
CREATE VIRTUAL TABLE media_fts USING fts5(
  title,
  original_title,
  overview,
  cast_crew,
  tags,
  content='media_item',
  content_rowid='id'
);
```

## 5. 实体变更摘要

| 表 | Phase | 说明 |
|----|-------|------|
| library_plugin_config | 2 | 统一插件配置 |
| ai_suggestion | 3 | AI 审核 |
| media_embedding | 3 | 向量 |
| media_fts | 3 | FTS5 |

## 6. 保留表（无变更）

sys_*、media_library、media_item、media_file、movie_metadata、tv_show_metadata、scrape_schedule、scrape_task、user_favorite、user_playback_history 等。

## 7. 未来：PostgreSQL

触发条件：单库 >50万 item 或需 pgvector。ADR-001 修订时提供 `V1_pg_baseline` 与双写迁移工具。
