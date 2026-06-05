# MediaManager v2 — SSE 事件字典

> 目标：给出**单页权威**的事件名、通道、权限、payload 形态、生产者与前端消费者，并明确 legacy 事件的弃用策略。

## 1. 两条 SSE 通道

| 通道 | 路径 | 权限 | 用途 |
|------|------|------|------|
| **业务事件** | `GET /api/v1/sse/events?clientId=&token=` | `task:view` | 扫描/刮削进度、库变更等“业务进度事件” |
| **系统日志** | `GET /api/v1/system/logs/stream?token=` | `system:manage` | 运维/审计日志（Logback Appender + 服务端广播） |

## 2. 业务事件（推荐契约）

> 业务事件由后端 [`SseService`](media-manager-server/src/main/java/com/mediamanager/sync/service/SseService.java) 广播；建议前端只监听“契约名”。

| 事件名 | 生产者 | payload（JSON） | 备注 |
|--------|--------|------------------|------|
| `scan.progress` | [`LibraryScanService`](media-manager-server/src/main/java/com/mediamanager/library/service/LibraryScanService.java) | **两种**：`ScanProgressDTO` 或字符串 | 全库扫描为结构化 DTO；Watch 增量扫描历史上可能发送字符串（逐步收敛） |
| `scrape.task.updated` | [`ScrapeTaskService`](media-manager-server/src/main/java/com/mediamanager/metadata/service/ScrapeTaskService.java) | `{ taskId, status?, scraped, errors, total? }` | 运行中应带 `status=RUNNING`；结束时 `status=SUCCESS/FAILED/CANCELLED` |
| `library.updated` | [`MediaLibraryService`](media-manager-server/src/main/java/com/mediamanager/library/service/MediaLibraryService.java)、`LibraryScanService` | `{ libraryId, action }` | action: created/updated/deleted/scan_completed |

### 2.1 `ScanProgressDTO` 字段（结构化）

对应前端 [`global.ts`](media-manager-web/src/models/global.ts) 的 `ScanProgress`：

```json
{
  "libraryId": 1,
  "libraryName": "Movies",
  "status": "SCANNING",
  "currentPath": "D:\\Media\\Movies",
  "totalFiles": 1234,
  "scannedFiles": 456,
  "matchedFiles": 400,
  "newItems": 12,
  "startedAt": 1710000000000,
  "updatedAt": 1710000001234
}
```

## 3. 业务事件（legacy，逐步弃用）

> legacy 事件名仍会被广播以兼容旧前端，但**不应再新增消费者**。

| legacy 事件 | 对应契约 | 备注 |
|------------|----------|------|
| `scan-status` | `scan.progress` | 历史兼容 |
| `scan-start` / `scan-end` | `scan.progress`（或系统日志） | 纯文本 |
| `scan-progress` | `scan.progress` | 纯文本（Watch/阶段消息） |
| `scrape-start` / `scrape-end` | `scrape.task.updated`（或系统日志） | 纯文本 |
| `scrape-progress` | `scrape.task.updated` | 历史兼容（应收敛到契约名） |

## 4. 系统日志事件（`/system/logs/stream`）

系统日志事件名固定为 `log`，payload 为 `SystemLogEventDto`（后端在 [`SystemLogBroadcaster`](media-manager-server/src/main/java/com/mediamanager/system/service/SystemLogBroadcaster.java) 里维护 emitter 列表）。

典型字段：

```json
{
  "timestamp": "2026-05-26T01:23:45.678Z",
  "level": "INFO",
  "source": "SCRAPE",
  "type": "SCRAPE_DONE",
  "message": "Scrape task 12 finished: 10 scraped, 0 errors",
  "libraryId": 3
}
```

## 5. 前端消费者对照

| 前端位置 | 消费通道 | 关注事件 |
|----------|----------|----------|
| [`models/global.ts`](media-manager-web/src/models/global.ts) | 业务 SSE | `scan.progress`、`scrape.task.updated`、`library.updated`（以及少量 legacy） |
| [`ScanProgressBanner`](media-manager-web/src/components/ScanProgressBanner/index.tsx) | global model | 扫描进行中条目 |
| [`ScrapeProgressBanner`](media-manager-web/src/components/ScrapeProgressBanner/index.tsx) | global model | 刮削进行中条目（依赖 `status=RUNNING/PENDING`） |
| [`System/Logs`](media-manager-web/src/pages/System/Logs.tsx) | 系统日志 SSE | `log` |

## 6. 已移除 / 未实现

- `file-added`：前端曾监听，但后端没有生产者；应删除监听或实现生产者后再写入字典。

