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
| MOVIE | 电影 | nfo, ffprobe, tmdb |
| TV_SHOW | 剧集 | nfo, ffprobe, tmdb |
| MUSIC | 音乐 | ffprobe, musicbrainz（远期） |
| PHOTO | 照片 | exif |
| MIXED | 混合 | 按文件魔数 |

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

**产品决策（Phase 4）**：

- **方案 A**：完整季集 UI（Browse → 剧 → 季 → 集）。
- **方案 B**：扁平化，每集作为 `EPISODE` 类型独立 Item。

当前实现偏 B，v2 文档要求显式选型后统一 API。

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
- Job：每日清理 `deleted_at < now - 30d` 的 DB 记录（不删源文件，除非用户此前选择删文件）。

## 6. 用户活动

| 表 | 用途 |
|----|------|
| user_favorite | 收藏 |
| user_playback_history | 继续观看、推荐 |

API 已有：`UserActivityController`。

## 7. 扫描

- 手动：`POST /libraries/{id}/scan`
- 定时：`ScheduledScanJob`
- 实时：`DirectoryWatcherService` + `EventDebouncer`

进度经 SSE `scan.progress` 推送。
