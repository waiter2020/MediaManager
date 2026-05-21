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

## 2. 实现状态快照（Phase 0 审计）

| 能力 | Legacy 声称 | 实际（Phase 0） | v2 目标 Phase |
|------|-------------|-----------------|---------------|
| SQLite | 01 架构 | ✅ | 保持 |
| 库级权限过滤 | 01 §4.4 | ❌ 仅配置 API | P1 |
| HLS | 06、07、M6 | ❌ | P1 |
| 手动 identify | 07、M3 | ❌ | P1 |
| 回收站 30 天 | M5 | ❌ 仅 deleted 字段 | P1 |
| access.ts | 08 | ❌ | P1 |
| xgplayer | 08、M6 | ❌ 原生 video | P1 |
| 插件统一 | 03 | ❌ 仅 Extractor | P2 |
| JavBus/StashDb | 无文档 | ✅ 代码有 | P2 文档化 |
| 刮削计划 | 无文档 | ✅ | P2 状态机 |
| FTS/语义搜 | 无 | ❌ | P3 |
| AiProvider | 04 预留 | ❌ | P3 |
| 集成测试 | M7 隐含 | ❌ 零测试 | P1 |
| deployment.md | M7.11 | ❌ 仅 README | P0 |

## 3. 代码与 v2 模块对照

| 现有包 | v2 模块 | 缺口 |
|--------|---------|------|
| library | library | 插件配置迁移 |
| media | catalog | 回收站、权限过滤 |
| metadata | metadata | Scraper 分离 |
| classification | classification | AiClassifier |
| streaming | streaming | HLS |
| sync | sync | - |
| system | security + system | LibraryAccessEnforcer |
| - | plugin | 新建 |
| - | ai | 新建 |
| - | search | 新建 |

## 4. 维护约定

- 新功能只写入 `docs/v2/`，legacy 只读参考。
- `docs/09-milestones.md` 顶部加 banner 指向 v2/11。
- 每个 Phase 结束更新本表「实际」列。
