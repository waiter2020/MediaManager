# MediaManager v2 — 检索与推荐

## 1. 三层检索

| 层 | 技术 | API | 场景 |
|----|------|-----|------|
| 结构化 | JPA Specification | `GET /items` + query params | 标签、类型、年份、库 |
| 全文 | SQLite FTS5 | `GET /search?q=` | 关键词 |
| 语义 | Embedding + cosine | `POST /search/semantic` | 自然语言、概念搜 |
| 组合 | AiProvider NL 解析 | `POST /search/query` | 「2020年后的科幻片」 |

## 2. 结构化查询（现有增强）

保留 `GET /api/v1/items/filters` 聚合 facet。

查询参数：`type`, `libraryId`, `tagIds`, `categoryIds`, `keyword`, `year`, `minRating`, `sort`。

**强制**：`libraryId` 必须在 `allowedLibraryIds` 内。

## 3. FTS5 设计

### 3.1 虚表

```sql
CREATE VIRTUAL TABLE media_fts USING fts5(
  title, original_title, overview, cast_crew, tags,
  content='media_item', content_rowid='id'
);
```

### 3.2 索引 Job

触发：扫描入库、元数据更新、标签变更。

异步队列，批量 `INSERT INTO media_fts(...)`。

### 3.3 查询

```sql
SELECT item_id FROM media_fts WHERE media_fts MATCH ? ORDER BY rank;
```

再经权限过滤 JOIN `media_item`。

## 4. 语义检索

### 4.1 表 `media_embedding`

| 字段 | 类型 |
|------|------|
| media_item_id | INT PK (with model_id) |
| model_id | VARCHAR |
| vector | BLOB |
| updated_at | BIGINT |

### 4.2 流程

1. 用户输入 query → `AiProvider.embedText`。
2. 在库允许范围内 brute-force cosine Top-K。
3. 返回 item 列表 + score。

### 4.3 API

`POST /api/v1/search/semantic`

```json
{ "query": "太空探险", "libraryId": 1, "limit": 20 }
```

## 5. NL 组合查询

`POST /api/v1/search/query`

```json
{ "query": "找评分高于8的诺兰电影", "libraryId": 1 }
```

1. `AiProvider.parseNaturalLanguage` → `StructuredQuery`。
2. 执行 Specification + 可选 FTS/semantic 加权。
3. 响应含 `explanation`（可选 LLM 生成）。

## 6. 推荐

### 6.1 信号

| 信号 | 来源 |
|------|------|
| 播放历史 | `user_playback_history` |
| 收藏 | `user_favorite` |
| 内容相似 | 同标签/类型/embedding |
| 热度 | 库内播放次数 |

### 6.2 API

`GET /api/v1/discover`

```json
{
  "continueWatching": [...],
  "recommended": [...],
  "recentlyAdded": [...]
}
```

### 6.3 冷启动

- 新用户：最近添加 + 库内高评分。
- 新库：按类型均匀抽样。

### 6.4 AI 解释（Phase 4）

`recommended[].reason` 由 `AiProvider.explainRecommendation` 生成（可选）。

## 7. 性能

| 规模 | 策略 |
|------|------|
| < 1万 item | SQLite FTS + 暴力向量 |
| 1万~10万 | 分批 embed；向量索引夜间重建 |
| > 10万 | 评估 pgvector / 专用向量库（ADR 修订） |
