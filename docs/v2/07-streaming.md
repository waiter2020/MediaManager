# MediaManager v2 — 点播服务

## 1. 能力矩阵

| 能力 | 协议 | 路径 |
|------|------|------|
| 直链 Range | HTTP 206 | `GET /api/v1/stream/{fileId}` |
| HLS 转封装 | m3u8 + ts | `GET /api/v1/stream/{fileId}/hls/master.m3u8` |
| 原图 | - | `GET /api/v1/images/{fileId}` |
| 缩略图 | query w/h | `GET /api/v1/images/{fileId}?w=300` |
| 海报/背景 | - | `GET /api/v1/items/{id}/poster` |

## 2. 直链流

- `ResourceRegion` 支持 Range。
- Chunk 默认 1MB。
- Content-Type 来自 `MediaFile.mimeType`。

## 3. HLS 转封装

### 3.1 触发条件

浏览器不原生支持的容器（如 mkv、avi）或 `?format=hls` 强制。

### 3.2 FFmpeg

```bash
ffmpeg -y -i "{input}" -c copy -f hls \
  -hls_time 6 -hls_list_size 0 \
  -hls_segment_filename "{cacheDir}/{fileId}/%04d.ts" \
  "{cacheDir}/{fileId}/index.m3u8"
```

### 3.3 缓存

- 目录：`{data.cache-dir}/hls/{fileId}/`
- 源文件 mtime 变化时失效重建。
- 定时任务清理 N 天未访问的 HLS 目录。

### 3.4 安全

- 校验 `fileId` 归属库在 `allowedLibraryIds`。
- 禁止 `..` 路径。

## 4. 动态转码（Phase B / 远期）

`GET /stream/{fileId}?quality=720p` — 需 FFmpeg 转码，CPU 密集，默认关闭。

## 5. 缩略图

`ThumbnailService`：视频帧 / 图片缩放，缓存至 `thumbnail-dir`。

## 6. 前端播放器

- 组件：`components/VideoPlayer`（xgplayer）。
- HLS 插件：hls.js 或 xgplayer-hls。
- `Player.tsx`：根据 API 返回 `playbackMode: direct | hls` 选择源。

## 7. 路径映射

容器部署：

```yaml
MEDIAMANAGER_STORAGE_PATH_MAP_FROM: E:\Movies
MEDIAMANAGER_STORAGE_PATH_MAP_TO: /home/media
```

`StreamService.mapPathIfNeeded` 应用映射。
