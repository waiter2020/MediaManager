# MediaManager — 点播服务

## 1. 设计概述

点播服务提供视频流式传输、图片浏览、音频播放能力，支持 HTTP Range 断点续传、HLS 自适应码率、缩略图生成。

## 2. 视频点播

### 2.1 直接流 (Direct Stream)

对于浏览器原生支持的容器格式 (MP4/WebM)，使用 HTTP Range 直接传输：

```java
@GetMapping("/api/v1/stream/{fileId}")
public ResponseEntity<ResourceRegion> streamVideo(
        @PathVariable Long fileId,
        @RequestHeader HttpHeaders headers) {

    MediaFile file = mediaFileService.getById(fileId);
    Resource resource = new FileSystemResource(file.getFilePath());

    // 支持 Range 请求 (断点续传)
    ResourceRegion region = resourceRegion(resource, headers);
    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(MediaType.parseMediaType(file.getMimeType()))
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .body(region);
}
```

### 2.2 HLS 点播 (可选转封装)

对于非原生格式 (MKV/AVI 等)，通过 FFmpeg 实时转封装为 HLS：

```
GET /api/v1/stream/{fileId}/hls/master.m3u8   → HLS 主播放列表
GET /api/v1/stream/{fileId}/hls/{segment}.ts   → 分片
```

**FFmpeg 转封装命令** (不转码, 仅换容器):
```bash
ffmpeg -i input.mkv -c copy -f hls \
  -hls_time 6 -hls_list_size 0 \
  -hls_segment_filename '/data/cache/hls/{fileId}/%04d.ts' \
  /data/cache/hls/{fileId}/index.m3u8
```

### 2.3 动态转码 (可选)

按需降低分辨率/码率以适配低带宽：

```
GET /api/v1/stream/{fileId}?quality=720p
GET /api/v1/stream/{fileId}?quality=480p
```

## 3. 图片点播

### 3.1 原图访问

```
GET /api/v1/images/{fileId}          → 原图
GET /api/v1/images/{fileId}?w=300    → 指定宽度缩略图
GET /api/v1/images/{fileId}?w=300&h=200  → 指定尺寸
```

### 3.2 缩略图生成策略

- 首次请求时生成，缓存到 `/data/cache/thumbnails/{fileId}/`
- 预设尺寸: 150px (列表), 300px (网格), 800px (预览)
- 使用 Java ImageIO / Thumbnailator 库
- 自动校正 EXIF 旋转信息
- 支持 WebP 输出以减小体积

### 3.3 海报与背景图

媒体项的海报/背景图（从 NFO 同目录或 TMDb 获取）：

```
GET /api/v1/items/{itemId}/poster         → 海报
GET /api/v1/items/{itemId}/backdrop        → 背景图
GET /api/v1/items/{itemId}/poster?w=200    → 缩略海报
```

## 4. 音频点播

```
GET /api/v1/stream/{fileId}   → 音频流 (支持 Range)
```

浏览器原生支持 MP3/AAC/OGG，其他格式使用 FFmpeg 实时转码为 AAC。

## 5. 缓存策略

| 资源 | Cache-Control | 说明 |
|------|--------------|------|
| 视频流 | no-cache | 每次验证 |
| HLS 分片 | max-age=86400 | 不变分片可长缓存 |
| 原图 | ETag + max-age=3600 | 基于文件修改时间 ETag |
| 缩略图 | max-age=604800 | 生成后不变，长缓存 |
| 海报/背景图 | max-age=86400 | 元数据刷新后失效 |

## 6. 安全

- 仅允许访问已注册媒体库路径下的文件
- 路径遍历防护: 规范化路径后校验是否在白名单目录下
- 流式传输需验证 `media:play` 权限
- 媒体库级权限校验 (LibraryAccess)
