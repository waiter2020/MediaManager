# MediaManager v2 — 插件系统

## 1. 设计目标

- 统一注册 **Extractor / Scraper / Classifier / AiProvider** 四类扩展点。
- 媒体库级启用与优先级配置，JSON Schema 驱动管理端表单。
- 插件失败隔离：单插件异常不中断整条管线（与现有 `MetadataPipelineService` 行为一致）。

## 2. 插件分类

| PluginKind | 触发时机 | 示例 |
|------------|----------|------|
| `EXTRACTOR` | 扫描、单条 refresh | NFO, FFPROBE, EXIF |
| `SCRAPER` | 异步 ScrapeTask | TMDB, JavBus, StashDb |
| `CLASSIFIER` | 元数据写入后 | MetadataClassifier, RuleBased, AI_CLASSIFIER |
| `AI_PROVIDER` | 补全、打标、Embedding、NL 解析 | Ollama, OpenAI-compatible |

## 3. 核心接口

```java
public enum PluginKind {
    EXTRACTOR, SCRAPER, CLASSIFIER, AI_PROVIDER
}

public interface MediaManagerPlugin {
    String id();                    // 全局唯一，如 "tmdb"
    PluginKind kind();
    String displayName();
    int defaultPriority();
    boolean supports(PluginContext ctx);
}

public record PluginContext(
    MediaLibrary library,
    MediaItem item,
    MediaFile primaryFile,
    MediaType mediaType
) {}
```

### 3.1 Extractor（兼容现有 SPI）

```java
public interface MetadataExtractor extends MediaManagerPlugin {
    @Override default PluginKind kind() { return PluginKind.EXTRACTOR; }
    MetadataResult extract(ExtractorContext ctx, LibraryPluginConfig config);
}
```

合并规则：**已有字段不被后置覆盖**，仅补充空缺（`MetadataResult.mergeFrom`）。

### 3.2 Scraper

```java
public interface MetadataScraper extends MediaManagerPlugin {
    ScrapeResult scrape(ScrapeContext ctx, LibraryPluginConfig config);
}
```

- 输入：item、文件名解析结果、已有 externalId。
- 输出：结构化 `ScrapeResult`（海报 URL、元数据字段、externalIds）。
- 由 `ScrapeTaskService` 调用，支持重试与超时。

### 3.3 Classifier

```java
public interface ClassifierStrategy extends MediaManagerPlugin {
    ClassificationResult classify(MediaItem item, MediaFile file, Object typedMetadata);
    int getPriority();
}
```

### 3.4 AiProvider

见 [04-ai-platform.md](./04-ai-platform.md)。

## 4. 注册与发现

```java
@Service
public class PluginRegistry {
    private final Map<String, MediaManagerPlugin> byId;
    List<MediaManagerPlugin> listByKind(PluginKind kind);
    MediaManagerPlugin require(String id);
}
```

- Spring 自动注入所有 `MediaManagerPlugin` 实现。
- 内置插件：同 jar 内 `@Component`。
- 远期：外部 jar / zip manifest（Phase 5）。

## 5. 库级配置

### 5.1 表：`library_plugin_config`（Phase 2 迁移）

| 字段 | 说明 |
|------|------|
| library_id | FK |
| plugin_id | 如 `tmdb` |
| kind | EXTRACTOR / SCRAPER / ... |
| enabled | bool |
| priority | int，越小越先 |
| config | JSON，插件自有 Schema |

**兼容**：从 `library_extractor_config` 迁移，`extractor_type` → `plugin_id`，`kind=EXTRACTOR`。

### 5.1.1 单一配置源（2026-05 重审）

| 存储 | 角色 |
|------|------|
| `library_plugin_config` | **权威**：扫描/刮削管线、`GET /libraries/{id}` 的 `plugins[]` |
| `library_extractor_config` | **遗留表**：仅当客户端仍 `PUT /libraries/{id}` 携带 `extractors[]` 时写入并 `syncFromExtractorConfigs` |

`PUT /libraries/{id}/plugins` **不再**回写 legacy 表。API 响应中的 `extractors[]` 由 `plugins[]` 中 `kind=EXTRACTOR` 派生，标记 **deprecated**，计划在 v2.1 移除。

前端：仅消费 `plugins[]`；库详情/插件页展示 SCRAPER 缺失警告。

### 5.2 配置 Schema

每个插件可提供 `config-schema.json`（JSON Schema Draft 07），用于：

- 后端保存前校验
- 前端 `Settings/Plugins` 动态表单

示例（TMDb）：

```json
{
  "type": "object",
  "properties": {
    "api_key": { "type": "string" },
    "language": { "type": "string", "default": "zh-CN" }
  },
  "required": ["api_key"]
}
```

## 6. 执行顺序

### 6.1 Extractor 链

```
enabled extractors
  .filter(supports)
  .sortBy(priority)
  .forEach(extract → mergeFrom)
```

### 6.2 Scraper 队列

```
ScrapeSchedule (cron) → ScrapeTask (PENDING)
  → worker poll → SCRAPER plugins by priority
  → persist → optional AI suggest → SSE
```

## 7. 失败与超时

| 策略 | 值 |
|------|-----|
| 单 Extractor 超时 | 30s（可配置） |
| Scraper HTTP 超时 | 15s，重试 2 次 |
| 失败记录 | `scrape_task.error_message` / 日志 |

## 8. 内置插件清单（当前代码 → v2 注册 id）

| plugin_id | kind | 类 |
|-----------|------|-----|
| nfo | EXTRACTOR | NfoExtractor |
| ffprobe | EXTRACTOR | FfprobeExtractor |
| exif | EXTRACTOR | ExifExtractor |
| tmdb | SCRAPER | TmdbExtractor（迁移为 Scraper） |
| javbus | SCRAPER | JavBusExtractor |
| stashdb | SCRAPER | StashDbExtractor |
| metadata | CLASSIFIER | MetadataClassifier |
| rule | CLASSIFIER | RuleBasedClassifier |

> TMDb 当前实现为 `MetadataExtractor`，Phase 2 拆为 SCRAPER 或保留双模式（扫描轻量 / 刮削重量）。

## 9. 权限

| 操作 | 权限 |
|------|------|
| 查看库插件配置 | `library:view` |
| 修改库插件配置 | `library:edit` |
| 全局 AI Provider 密钥 | `system:manage` |
