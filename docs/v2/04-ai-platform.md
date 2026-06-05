# MediaManager v2 — AI 平台

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| 可插拔混合 | 库/任务级选择本地或云端 Provider |
| 默认保守 | 无配置则跳过 AI；敏感库可 `ai.outbound=false` |
| 人机协同 | AI 写入须经审核（`review_status`）方可落库 |
| 可观测 | 记录 provider、model、latency、token（若适用） |

## 2. AiProvider SPI

```java
public interface AiProvider extends MediaManagerPlugin {
    @Override default PluginKind kind() { return PluginKind.AI_PROVIDER; }

    /** 文本向量，用于语义检索 */
    float[] embedText(EmbedRequest req);

    /** 图片向量（Phase 4） */
    Optional<float[]> embedImage(EmbedRequest req);

    /** 元数据字段补全建议 */
    MetadataSuggestion completeMetadata(MetadataCompleteRequest req);

    /** 标签建议 */
    List<TagSuggestion> suggestTags(TagSuggestRequest req);

    /** 自然语言 → 结构化查询 */
    StructuredQuery parseNaturalLanguage(NlQueryRequest req);

    /** 推荐解释文案（可选） */
    Optional<String> explainRecommendation(ExplainRequest req);
}
```

## 3. 内置 Provider 实现

| provider_id | 类型 | 用途 |
|-------------|------|------|
| `ollama` | 本地 | LLM 补全、NL 解析；`nomic-embed-text` 等 Embedding |
| `openai-compatible` | 云端 | 任意 OpenAI API 兼容端点 |
| `noop` | 降级 | 未配置时返回空 |

### 3.1 配置层级

```
全局 sys_config.ai.*
  └── 媒体库 library_plugin_config (kind=AI_PROVIDER)
        └── 任务级覆盖（ScrapeTask.params）
```

示例：

```json
{
  "provider_id": "ollama",
  "base_url": "http://host.docker.internal:11434",
  "llm_model": "qwen2.5:7b",
  "embed_model": "nomic-embed-text",
  "timeout_ms": 60000,
  "outbound_allowed": true
}
```

## 4. AiOrchestrator

```java
@Service
public class AiOrchestrator {
    AiProvider resolve(Integer libraryId, AiTaskType task);
    void completeMetadataAsync(MediaItem item);
    float[] embedForItem(MediaItem item);
    StructuredQuery parseQuery(String nl, Integer libraryId);
}
```

**路由规则**：

1. 库级 `AI_PROVIDER` 配置且 `enabled`。
2. 库配置 `outbound_allowed=false` 时仅允许 `ollama` / 本地类 provider。
3. 回退全局默认；再无则 `noop`。

## 5. 应用场景

| 场景 | 触发 | 输出 | 落库 |
|------|------|------|------|
| 元数据补全 | 刮削后 / 手动 | title, overview, genres... | `ai_suggestion` PENDING |
| 智能打标 | 分类链 | TagSuggestion + confidence | 同上或直写 tag（需审核配置） |
| 语义检索 | 用户搜索 | itemId 排序列表 | 不写库 |
| NL 查询 | 搜索框 | StructuredQuery | 不写库 |
| 推荐解释 | Discover 页 | 文案 | 不写库 |
| 以图搜图 | 图片详情 | 相似 itemIds | Phase 4 |

## 6. 审核流

### 6.1 表 `ai_suggestion`

| 字段 | 说明 |
|------|------|
| id | PK |
| media_item_id | FK |
| field_name | 如 `overview` |
| suggested_value | TEXT/JSON |
| source | AI |
| provider_id | |
| confidence | 0~1 |
| review_status | PENDING / APPROVED / REJECTED |
| reviewed_by | user id |
| created_at | |

### 6.2 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ai/suggestions` | 分页待审核 |
| POST | `/api/v1/ai/suggestions/{id}/approve` | 写入正式元数据 |
| POST | `/api/v1/ai/suggestions/{id}/reject` | 拒绝 |

权限：`media:edit_metadata` 或 `system:manage`。

## 7. 向量与索引

- 表 `media_embedding`：`(item_id, model_id, vector BLOB, updated_at)`。
- 索引 Job：扫描/元数据变更后异步 `embedForItem`。
- 检索：小规模 cosine 暴力搜索；>10万 考虑 sqlite-vec 或外置 pgvector。

## 8. 隐私与合规

| 控制 | 说明 |
|------|------|
| `library.ai.outbound_allowed` | false 时禁止云端 Provider |
| 日志脱敏 | 不记录 API Key、完整文件路径 |
| 用户告知 | 设置页说明哪些库启用 AI |
| 外部刮削源 | JavBus 等需管理员显式启用 |

## 9. 错误处理

| 错误 | 行为 |
|------|------|
| Provider 超时 | 任务标记 FAILED，可重试 |
| 速率限制 | 指数退避 |
| 无效 JSON 输出 | 丢弃建议，记 warn 日志 |
| Embedding 维度不一致 | 拒绝写入并告警 |

## 10. 与分类引擎集成

新增 `AiClassifier`（`ClassifierStrategy`）：

- 调用 `AiProvider.suggestTags`。
- `TagSuggestion.source = AI`，`confidence` 来自模型或固定 0.8。
- 默认仅写 `ai_suggestion`，配置 `ai.auto_apply_tags=true` 时高置信度直写。
