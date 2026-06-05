# MediaManager v2 — 目录与媒体库

## 1. 媒体库

### 1.1 实体关系

```
MediaLibrary 1──* LibraryPath
             1──* LibraryPluginConfig
             1──* MediaItem
             1──* LibraryAccess
```

### 1.2 库类型

| type | 说明 | 默认插件 |
|------|------|----------|
| MOVIE | 电影 | nfo, ffprobe（EXTRACTOR）+ tmdb（SCRAPER） |
| TV_SHOW | 剧集 | 同上 |
| AUDIO | 音频 | ffprobe, nfo |
| IMAGE | 图片 | exif, ffprobe |
| MIXED | 混合 | nfo, ffprobe, exif + tmdb（SCRAPER） |

默认链由 `LibraryPluginConfigService.defaultPluginsForType` 写入 `library_plugin_config`；创建库向导与 `POST .../plugins/apply-default` 一致。

## 2. 媒体项与文件

### 2.1 MediaItem

- `type`: MOVIE | TV_SHOW | EPISODE | IMAGE | AUDIO
- `status`: UNIDENTIFIED | IDENTIFIED | PARTIAL
- `poster_path`, `backdrop_path`：缓存相对路径

### 2.2 MediaFile

- 一个 Item 多个 File（多版本/多碟）
- `deleted` + `deleted_at`：软删除
- `primary` 标记主文件

## 3. 剧集模型

```
TvShowMetadata 1──* Season 1──* Episode *──0..1 MediaFile
```

**产品决策**：

- **选定：方案 B（扁平 EPISODE）**：每集文件对应独立 `MediaItem.type=EPISODE`，播放与管理以 EPISODE 为主入口。
- `TV_SHOW` 用作“剧集壳”（TMDb 季集元数据容器），用于展示 Season/Episode 元数据索引，但不承载播放主入口。

兼容说明：历史数据中可能存在“多文件合并为一个 TV_SHOW”的旧扫描结果；本策略对**新扫描**生效，旧数据不自动拆分。

## 4. 库级权限

### 4.1 表 `library_access`

| 字段 | 说明 |
|------|------|
| user_id | |
| library_id | |
| permission | VIEW / EDIT / ADMIN |

### 4.2 生效规则（Phase 1 实现）

```java
Set<Integer> allowedLibraryIds(Integer userId) {
  if (isSuperAdmin(userId)) return ALL;
  List<LibraryAccess> rows = repo.findByUserId(userId);
  if (rows.isEmpty()) return inferFromGlobalRole(); // VIEW libraries for USER/GUEST
  return rows.stream().map(LibraryAccess::getLibraryId).collect(toSet());
}
```

| 全局角色 | 无 library_access 行时 |
|----------|------------------------|
| GUEST/USER | 仅 `library:view` 且需配置行或公开库策略（默认：无行=不可见任何库，除 SUPER_ADMIN） |
| ADMIN | 可见全部库（实现可选） |

**v2 默认严格模式**：无 `library_access` 行则不可见该库（ADMIN 除外）。

## 5. 回收站

- 软删：`MediaFile.deleted=true`, `deleted_at=now`。
- API：`GET /api/v1/recycle-bin`，`POST /api/v1/recycle-bin/{fileId}/restore`。
- Job：每日清理 `deleted_at < now - 30d` 的 DB 记录（默认不删源文件）。

永久删除：`DELETE /api/v1/recycle-bin/{fileId}?deleteSource=false|true`。\n

## 6. 用户活动

| 表 | 用途 |
|----|------|
| user_favorite | 收藏 |
| user_playback_history | 继续观看、推荐 |

API 已有：`UserActivityController`。

## 7. 扫描、刮削与插件链

| 动作 | API | 使用的插件 kind |
|------|-----|-----------------|
| 扫描入库 | `POST /libraries/{id}/scan` | 仅 **EXTRACTOR**（NFO / FFprobe / EXIF），不触发网络 SCRAPER |
| 刮削任务 | `POST /scrape/tasks` 等 | **SCRAPER** + 可选本地 EXTRACTOR（`MetadataPipelineService.executeScrapePipeline`） |
| 库级重分类 | `POST /libraries/{id}/classify` | **CLASSIFIER**（与插件表无直接行，由分类引擎调度） |

配置读写：

- **首选**：`GET/PUT /libraries/{id}/plugins`、`POST .../plugins/apply-default`
- **已弃用**：库 CRUD 请求体中的 `extractors[]` 仍会同步到 `library_plugin_config`，但响应请以 `plugins[]` 为准

进度经 SSE `scan.progress` / `scrape.task.updated` 推送；事件字典见 [`docs/v2/13-sse-event-dictionary.md`](docs/v2/13-sse-event-dictionary.md)。
