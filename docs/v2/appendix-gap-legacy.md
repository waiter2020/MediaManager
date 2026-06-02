# Legacy 文档与 v2 差距对照

## 1. 文档映射

| Legacy | v2 对应 | 说明 |
|--------|---------|------|
| docs/01-architecture.md | v2/01-architecture.md | 补充 AI、search、plugin 模块 |
| docs/02-database-schema.md | v2/10-database-evolution.md | 增量表 |
| docs/03-metadata-pipeline.md | v2/03-metadata-and-scrape.md | 刮削分离 |
| docs/04-classifier-engine.md | v2/02 + v2/04 | AI Classifier |
| docs/05-file-watcher.md | v2/01 §4、v2/06 | 无结构性变化 |
| docs/06-streaming-service.md | v2/07-streaming.md | HLS 待实现→已实现（Phase 1） |
| docs/07-api-design.md | v2/08-api-contract.md | 新增 search/ai/recycle |
| docs/08-frontend-design.md | v2/09-frontend-ia.md | access、Search、Review |
| docs/09-milestones.md | v2/11-implementation-plan.md | **废弃虚假 [x]** |

## 2. 实现状态快照（2026-05-22 系统完善后）

| 能力 | Legacy 声称 | 实际 | 备注 |
|------|-------------|------|------|
| SQLite | 01 架构 | ✅ | 保持 |
| 库级权限过滤 | 01 §4.4 | ✅ | `canEdit`/`canDeleteFile`  enforced；Tag/删除/库 CRUD |
| HLS | 06、07、M6 | ✅ | `HlsStreamingService` |
| 手动 identify | 07、M3 | ✅ | TMDb / JavBus / StashDB + `IdentifyModal` |
| 回收站 | M5 | ✅ | 列表/恢复；purge 删物理文件；30 天 Job |
| access.ts + initialState | 08 | ✅ | `/users/me` 含 permissions |
| xgplayer + HLS | 08、M6 | ✅ | `VideoPlayer` |
| 插件注册表 | 03 | ✅ | Spring Bean 注册 + 库级配置 |
| JavBus/StashDb | 无文档 | ✅ | identify 与刮削链 |
| 刮削状态机 | - | ✅ | 非法迁移日志 |
| 扫描仅本地链 | - | ✅ | `executeLocalPipeline` |
| FTS/语义搜 | - | ✅ | FTS5 + Ollama embed + 空结果提示 |
| AiProvider | 04 | ✅ | Ollama 默认 + health |
| 集成测试 | M7 | ✅ | `Phase1ApiIntegrationTest` 等 8 场景 |
| SSE 契约名 | - | ✅ | 双发 `scan.progress` / `scrape.task.updated` |
| `/discover` 页 | 08 | ✅ | 继续观看 → 播放器续播 |
| 个人设置 | 08 | ✅ | `/settings/profile` 改密/资料 |
| 设置中心布局 | 09 | ✅ | `/settings/*` + `_layout` 侧栏；旧路径 redirect |
| Typed 系统设置 API | - | ✅ | `/system/settings/{security,media-processing,...}` |
| `/settings/plugins` | 09 Phase 2 | ✅ | 全局插件注册表页 |
| 按库类型默认插件 | 06 | ✅ | `LibraryPluginConfigService.defaultPluginsForType` |
| 库 API `plugins[]` | - | ✅ | `MediaLibraryResponse.plugins` |
| 创建库三步向导 | milestones | ✅ | `Library/Create` Steps |
| `POST .../plugins/apply-default` | - | ✅ | 恢复类型默认链 |
| 刮削/任务 IA 联动 | 03 | ✅ | Tasks ↔ Schedules 说明与跳转 |
| 分类/搜索/审核交叉导航 | 08/09 | ✅ | 规则、AI 设置入口 |
| 模块深化日志 | - | ✅ | `docs/v2/12-module-refinement-log.md` |
| 播放器续播 | 08 | ✅ | `?t=` + 进度上报 |
| 分类规则 UI | - | ✅ | `Settings/Rules` CRUD |
| deployment.md | M7.11 | 部分 | 见 `docs/deployment.md` |
| TV_SHOW 详情 tvShowMetadata | - | ✅ | `getItemDetail` + Detail `buildTvShowVM` |
| 分类规则执行 | - | ✅ | `DatabaseRuleClassifier` + `POST /items/{id}/classify` |
| sys_config 驱动运行时 | - | ✅ | `SysConfigService` + AI/TMDb/auth 回退 |
| AI 库级路由 | 04 | ✅ | `AiOrchestrator` + `library_plugin_config` |
| NL 年份/评分过滤 | - | ✅ | `MediaItemSpecification` + `SearchService` |
| 语义搜 score | - | ✅ | `SemanticSearchItem` |
| JavBus/StashDB 搜索 | - | ✅ | `/javbus/search` `/stashdb/search` + IdentifyModal |
| 插件 config JSON UI | - | ✅ | `PluginExtractorConfigForm` 结构化表单 |
| 默认插件配置 | - | ✅ | 新建库自动 NFO/FFprobe/TMDb |
| 批量/库级分类 | - | ✅ | `POST /items/classify-batch`、`POST /libraries/{id}/classify` |
| 插件↔legacy 双向同步 | - | ✅ | `LibraryPluginConfigService.syncToExtractorConfigs` |
| 相似推荐 / 以图搜图 v1 | 04 Phase 4 | ✅ | `GET /items/{id}/similar` + Detail 相似推荐行 |
| docker-compose 可移植性 | deployment | ✅ | 移除硬编码宿主机媒体路径 |

## 3. 代码与 v2 模块对照（2026-05-22 同步）

| 现有包 | v2 模块 | 状态 |
|--------|---------|------|
| library | library | ✅ 插件配置 `library_plugin_config` + V14 迁移 |
| media | catalog | ✅ 回收站、库权限、`POST /items/{id}/seasons/sync` |
| metadata | metadata | ✅ 本地/远程管线分离、TMDb 季集同步 |
| classification | classification | ✅ AiClassifier + DatabaseRuleClassifier |
| streaming | streaming | ✅ Range + HLS remux |
| sync | sync | ✅ SSE + WatchService |
| system | security + system | ✅ LibraryAccessService + SysConfigService |
| plugin | plugin | ✅ PluginRegistry + 库级配置 API |
| ai | ai | ✅ AiProvider SPI、建议审核、向量索引 |
| search | search | ✅ FTS5、语义搜、NL 查询、Discover |

## 4. 维护约定

- 新功能只写入 `docs/v2/`，legacy 只读参考。
- `docs/09-milestones.md` 顶部加 banner 指向 v2/11。
- 每个 Phase 结束更新本表「实际」列。
