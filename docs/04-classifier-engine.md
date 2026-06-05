# MediaManager — 分类打标引擎

## 1. 设计概述

分类打标引擎在 **数据库层面** 对媒体项进行多维度自动分类和标签管理，不影响源文件。

核心能力：
- **自动分类**: 根据元数据、文件属性、规则自动归类
- **标签管理**: 手动/自动标签，支持按元数据字段筛选
- **多维度筛选**: 按标签、分类、元数据字段联合查询
- **AI 分类预留**: 接口化设计，可接入 LLM

## 2. 架构

```
┌─────────────────────────────────────────────────┐
│              Classifier Engine                   │
│                                                  │
│  ┌────────────┐ ┌────────────┐ ┌──────────────┐ │
│  │ Metadata   │ │ Rule-Based │ │ AI Classifier│ │
│  │ Classifier │ │ Classifier │ │ (预留)       │ │
│  └─────┬──────┘ └─────┬──────┘ └──────┬───────┘ │
│        │              │               │          │
│        ▼              ▼               ▼          │
│  ┌─────────────────────────────────────────────┐ │
│  │         TagAssignmentService                │ │
│  │  统一标签分配 / 分类归属 / 冲突处理          │ │
│  └─────────────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
              数据库 (tag / category 关联表)
```

## 3. 分类器接口

```java
public interface ClassifierStrategy {
    /** 分类器标识 */
    String getType();

    /** 分析媒体项并返回建议的标签和分类 */
    ClassificationResult classify(MediaItem item, MediaFile file,
                                   Object metadata); // MovieMetadata / ImageMetadata 等

    /** 是否支持该媒体类型 */
    boolean supports(MediaType type);
}

public record ClassificationResult(
    List<TagSuggestion> tags,
    List<CategoryAssignment> categories
) {}

public record TagSuggestion(
    String tagName,
    String source,    // RULE / METADATA / AI
    double confidence // 0.0 ~ 1.0
) {}
```

## 4. 内置分类器

### 4.1 元数据分类器 (MetadataClassifier)

从已提取的元数据中自动提取标签和分类：

| 元数据字段 | 生成标签/分类 | 示例 |
|------------|-------------|------|
| `genres` | Genre 分类 + 标签 | Action, Sci-Fi, Drama |
| `release_date` | Year 分类 | 2024, 2023 |
| `cast[].name` | 演员标签 | 可按演员筛选 |
| `crew[].name` (director) | 导演标签 | 可按导演筛选 |
| `studios[]` | 制片公司标签 | |
| `certification` | 分级标签 | PG-13, R |
| `camera_make` (图片) | 相机标签 | Canon, Sony |
| `artist` / `album` (音频) | 艺术家/专辑标签 | |

**核心逻辑**：
```java
@Component
public class MetadataClassifier implements ClassifierStrategy {
    @Override
    public ClassificationResult classify(MediaItem item, MediaFile file, Object metadata) {
        var tags = new ArrayList<TagSuggestion>();
        var categories = new ArrayList<CategoryAssignment>();

        if (metadata instanceof MovieMetadata movie) {
            // 从 genres 生成标签和分类
            movie.getGenres().forEach(genre -> {
                tags.add(new TagSuggestion(genre, "METADATA", 1.0));
                categories.add(new CategoryAssignment("GENRE", genre));
            });
            // 年份分类
            if (item.getReleaseDate() != null) {
                categories.add(new CategoryAssignment("YEAR",
                    String.valueOf(item.getReleaseDate().getYear())));
            }
            // 演员/导演标签
            movie.getCast().forEach(actor ->
                tags.add(new TagSuggestion("actor:" + actor.getName(), "METADATA", 1.0)));
        }
        return new ClassificationResult(tags, categories);
    }
}
```

### 4.2 规则分类器 (RuleBasedClassifier)

基于用户自定义规则自动分类：

**规则模型**：
```java
public record ClassificationRule(
    Long id,
    String name,
    String targetField,    // "file_name", "file_path", "title", "codec", "resolution"
    String operator,       // "contains", "matches", "equals", "gt", "lt"
    String value,          // 匹配值或正则
    String assignTagName,  // 匹配时分配的标签
    String assignCategory  // 匹配时分配的分类
) {}
```

**内置规则示例**：

| 规则 | 字段 | 条件 | 分配 |
|------|------|------|------|
| 4K 分辨率 | width | >= 3840 | 标签: `4K`, 分类: RESOLUTION/4K |
| 1080p | width | >= 1920 且 < 3840 | 标签: `1080p`, 分类: RESOLUTION/1080p |
| 720p | width | >= 1280 且 < 1920 | 标签: `720p`, 分类: RESOLUTION/720p |
| HEVC 编码 | video_codec | contains "hevc\|h265" | 标签: `HEVC` |
| AV1 编码 | video_codec | contains "av1" | 标签: `AV1` |
| HDR | file_name | matches "HDR\|DoVi" | 标签: `HDR` |
| 路径分类 | file_path | contains /{value}/ | 分类: CUSTOM/{value} |

### 4.3 AI 分类器 (AiClassifier) — 预留

```java
public interface AiClassifier extends ClassifierStrategy {
    /**
     * 使用 LLM 分析媒体内容
     * 可基于: 标题/简介文本分析, 视频帧截取分析, 图片内容识别
     */
    CompletableFuture<ClassificationResult> classifyAsync(MediaItem item);
}

// 配置接口
public record AiClassifierConfig(
    String provider,    // "openai", "dashscope", "ollama"
    String modelName,   // "gpt-4o", "qwen-vl-max"
    String apiKey,
    String baseUrl,
    int maxTokens,
    String systemPrompt // 定义分类规则的 prompt
) {}
```

## 5. 标签与分类的筛选查询

### 5.1 多维度筛选 API

```
GET /api/v1/items?filters=
  type=MOVIE
  &tags=Action,4K
  &categories=GENRE/Sci-Fi,YEAR/2024
  &rating_min=7.0
  &has_metadata=genres,cast
  &sort=release_date:desc
  &page=1&size=20
```

### 5.2 按元数据字段筛选

支持按元数据具体字段值筛选：

| 筛选维度 | 参数 | 示例 |
|----------|------|------|
| 类型标签 (genre) | `genre=Action` | 筛选 Action 类型 |
| 演员 | `actor=xxx` | 筛选包含该演员的媒体 |
| 导演 | `director=xxx` | |
| 制片公司 | `studio=xxx` | |
| 分辨率标签 | `resolution=4K` | |
| 编码标签 | `codec=HEVC` | |
| 年份范围 | `year_from=2020&year_to=2024` | |
| 评分范围 | `rating_min=7&rating_max=10` | |
| 相机品牌(图片) | `camera=Canon` | |

### 5.3 数据库查询实现

使用 JPA Specification 动态组合查询条件：

```java
public class MediaItemSpecification {
    public static Specification<MediaItem> withFilters(MediaFilterRequest filters) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (filters.getType() != null)
                predicates.add(cb.equal(root.get("type"), filters.getType()));

            if (filters.getTags() != null && !filters.getTags().isEmpty())
                // JOIN media_item_tag + tag WHERE tag.name IN (...)
                predicates.add(root.join("tags").get("name").in(filters.getTags()));

            if (filters.getGenre() != null)
                // 使用 PostgreSQL JSONB 查询
                predicates.add(cb.isTrue(
                    cb.function("jsonb_exists", Boolean.class,
                        root.join("movieMetadata").get("genres"),
                        cb.literal(filters.getGenre()))));

            // ... 更多条件
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

## 6. 分类执行时机

| 触发事件 | 行为 |
|----------|------|
| `MetadataExtractedEvent` | 自动运行所有分类器 |
| 用户手动编辑元数据 | 重新运行元数据分类器 |
| 用户创建/修改规则 | 可选——对存量数据重跑规则分类器 |
| 定时任务 | 每日检查规则变更，增量重分类 |
