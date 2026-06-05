# MediaManager — 元数据提取管线

## 1. 设计概述

元数据提取采用 **优先级链式管线** 模式，参考 Jellyfin 的提取器链设计：
- 每个提取器（Extractor）是管线中的一个 Stage
- 按优先级顺序执行，**前置提取器的结果不被后置覆盖**
- 每个媒体库可独立配置启用的提取器及优先级

## 2. 管线执行流程

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ NFO      │───→│ FFprobe  │───→│ EXIF     │───→│ TMDb     │
│ Reader   │    │ /MediaInfo│    │ Reader   │    │ Scraper  │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
     │               │               │               │
     ▼               ▼               ▼               ▼
┌──────────────────────────────────────────────────────────┐
│                  MetadataResult (合并)                     │
│  规则: 已有字段不覆盖，仅补充空缺字段                       │
└──────────────────────────────────────────────────────────┘
     │
     ▼
  写入数据库 → 发布 MetadataExtractedEvent → 触发分类引擎
```

## 3. 提取器 SPI 接口

```java
public interface MetadataExtractor {
    /** 提取器标识 */
    String getType(); // "NFO", "FFPROBE", "TMDB", "EXIF", "MUSICBRAINZ"

    /** 是否支持该媒体类型 */
    boolean supports(MediaType mediaType);

    /** 提取元数据，currentResult 为前置提取器已有结果 */
    MetadataResult extract(Path filePath, MetadataResult currentResult);

    /** 默认优先级 (可被媒体库配置覆盖) */
    default int getDefaultOrder() { return 100; }
}
```

## 4. 内置提取器详解

### 4.1 NFO 提取器 (NfoExtractor)

兼容 Jellyfin / Kodi NFO XML 格式：

**文件查找规则**：
| 媒体类型 | NFO 文件名 |
|----------|-----------|
| 电影 | `<filename>.nfo` 或 `movie.nfo` |
| 剧集 (show) | `tvshow.nfo` |
| 剧集 (season) | `season.nfo` |
| 剧集 (episode) | `<filename>.nfo` |
| 音乐 (artist) | `artist.nfo` |
| 音乐 (album) | `album.nfo` |

**解析的 XML 标签**：
```xml
<movie>
  <title>电影标题</title>
  <originaltitle>Original Title</originaltitle>
  <sorttitle>Sort Title</sorttitle>
  <plot>剧情简介...</plot>
  <tagline>标语</tagline>
  <runtime>120</runtime>
  <year>2024</year>
  <premiered>2024-01-15</premiered>
  <rating>8.5</rating>
  <genre>Action</genre>
  <genre>Sci-Fi</genre>
  <studio>Studio Name</studio>
  <mpaa>PG-13</mpaa>
  <uniqueid type="tmdb">12345</uniqueid>
  <uniqueid type="imdb">tt1234567</uniqueid>
  <actor>
    <name>Actor Name</name>
    <role>Character</role>
    <thumb>actor_thumb.jpg</thumb>
  </actor>
  <director>Director Name</director>
  <trailer>https://youtube.com/watch?v=xxx</trailer>
</movie>
```

### 4.2 FFprobe 提取器 (FfprobeExtractor)

通过调用 FFprobe 提取技术元数据：

```java
// 执行命令
ffprobe -v quiet -print_format json -show_format -show_streams <file>
```

**提取字段**: container, video_codec, audio_codec, width, height, duration, bitrate, sample_rate, channels

### 4.3 EXIF 提取器 (ExifExtractor)

使用 `metadata-extractor` 库读取图片 EXIF/IPTC/XMP：

**提取字段**: camera_make, camera_model, lens, iso, aperture, shutter_speed, taken_at, gps, orientation

### 4.4 TMDb 提取器 (TmdbExtractor)

在线刮削元数据，通过 TMDb API v3：

**匹配策略**：
1. 优先使用 NFO 中已有的 TMDb/IMDb ID 直接查询
2. 回退到文件名/目录名解析 + 搜索 API

**提取字段**: title, overview, poster, backdrop, cast, crew, genres, rating, release_date, trailer

**配置项** (存于 `library_extractor_config.config` JSONB):
```json
{
  "api_key": "your_tmdb_api_key",
  "language": "zh-CN",
  "include_adult": false,
  "download_images": true,
  "image_cache_dir": "/data/cache/images"
}
```

### 4.5 MusicBrainz 提取器 (MusicBrainzExtractor)

音频媒体在线元数据提取（预留实现）。

## 5. 管线编排服务

```java
@Service
public class MetadataPipelineService {

    private final List<MetadataExtractor> extractors; // Spring 自动注入所有实现

    public MetadataResult execute(Path filePath, MediaType type, Long libraryId) {
        // 1. 获取媒体库的提取器配置（优先级+启用状态）
        List<ExtractorConfig> configs = getLibraryExtractorConfigs(libraryId);

        // 2. 按优先级排序过滤提取器
        List<MetadataExtractor> pipeline = buildPipeline(configs, type);

        // 3. 链式执行
        MetadataResult result = MetadataResult.empty();
        for (MetadataExtractor extractor : pipeline) {
            try {
                result = extractor.extract(filePath, result);
            } catch (Exception e) {
                log.warn("Extractor {} failed: {}", extractor.getType(), e.getMessage());
                // 单个提取器失败不影响管线继续
            }
        }
        return result;
    }
}
```

## 6. 文件名解析器

在 TMDb 匹配前，解析文件名/目录名提取信息：

**电影**: `Movie Name (2024) [1080p] [BluRay].mkv` → title="Movie Name", year=2024
**剧集**: `Show Name S02E05 - Episode Title.mkv` → show="Show Name", S=2, E=5
**支持模式**: `SxxExx`, `Season X/Episode Y`, `年份`, `分辨率标识`

## 7. 图片资源管理

海报/背景图/演员头像处理流程：
1. NFO 同目录查找: `poster.jpg`, `fanart.jpg`, `folder.jpg`
2. TMDb 下载: 下载到 `/data/cache/images/{mediaItemId}/`
3. 生成缩略图: 使用 Java ImageIO 按需生成多尺寸缩略图
4. 数据库存储路径，通过 API 提供访问
