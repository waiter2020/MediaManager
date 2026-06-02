# MediaManager v2 — API 契约

## 1. 通用规范

- **Base URL**: `/api/v1`
- **认证**: `Authorization: Bearer <accessToken>`（`mediamanager.auth.enabled=false` 时跳过）
- **响应**: `ApiResponse<T>` — `{ code, message, data, timestamp }`
- **分页**: `page`（从 1）、`size`（默认 20，最大 100）
- **OpenAPI**: `/swagger-ui.html`（SpringDoc）为机器可读权威来源

## 2. 错误码

| code | 含义 |
|------|------|
| 200 | 成功 |
| 40001 | 参数校验失败 |
| 40101 | Token 过期 |
| 40102 | 无效 Token |
| 40301 | 无权限 |
| 40302 | 无库访问权限 |
| 40401 | 媒体项不存在 |
| 40404 | 媒体文件不存在 |
| 50001 | 内部错误 |

## 3. 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录 |
| POST | `/auth/refresh` | 刷新 |
| POST | `/auth/logout` | 登出 |
| POST | `/auth/setup` | 首次安装 |
| GET | `/auth/setup/status` | 是否需初始化 |

## 4. 用户与库权限

| 方法 | 路径 | 权限 |
|------|------|------|
| GET/POST/PUT/DELETE | `/users` | user:manage |
| PUT | `/users/{id}/library-access` | user:manage |

## 5. 媒体库

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/libraries` | library:view（过滤 allowedLibraryIds） |
| POST | `/libraries` | library:create |
| GET | `/libraries/{id}` | library:view + 库权限 |
| PUT | `/libraries/{id}` | library:edit |
| DELETE | `/libraries/{id}` | library:delete |
| POST | `/libraries/{id}/scan` | library:scan |
| GET | `/libraries/{id}/stats` | library:view |
| POST | `/libraries/{id}/classify` | media:edit_metadata，库内异步重分类 |
| GET | `/libraries/classify/status` | media:edit_metadata，库级分类任务状态 |

## 6. 媒体项

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/items` | media:view + 库过滤（支持 `minYear`/`maxYear`/`minRating`） |
| GET | `/items/filters` | media:view |
| GET | `/items/{id}` | media:view |
| GET | `/items/{id}/detail` | media:view（含 `tvShowMetadata`） |
| GET | `/items/{id}/seasons` | media:view（TV 季集） |
| POST | `/items/{id}/seasons/sync` | media:refresh（从 TMDb 同步季集，需 `providerIds.tmdb`） |
| GET | `/items/{id}/similar` | media:view（向量相似推荐，`limit` 默认 12） |
| PUT | `/items/{id}/metadata` | media:edit_metadata |
| POST | `/items/{id}/refresh` | media:refresh |
| POST | `/items/{id}/classify` | media:edit_metadata |
| POST | `/items/classify-batch` | media:edit_metadata，body `{ itemIds }` 最多 100 |
| POST | `/items/{id}/identify` | media:edit_metadata |
| GET | `/items/{id}/tmdb/search` | media:edit_metadata |
| GET | `/items/{id}/javbus/search` | media:edit_metadata |
| GET | `/items/{id}/stashdb/search` | media:edit_metadata |
| DELETE | `/items/{id}` | media:delete |
| DELETE | `/items/{id}/file` | media:delete_file |
| GET | `/items/{id}/poster` | media:view |
| GET | `/items/{id}/backdrop` | media:view |

### identify 请求体

```json
{
  "provider": "tmdb",
  "externalId": "27205"
}
```

## 7. 回收站（Phase 1）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/recycle-bin` | media:view |
| POST | `/recycle-bin/{fileId}/restore` | media:delete |
| DELETE | `/recycle-bin/{fileId}` | media:delete（永久移除记录） |

## 8. 点播

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/stream/{fileId}` | media:play + 库权限 |
| GET | `/stream/{fileId}/hls/master.m3u8` | media:play |
| GET | `/stream/{fileId}/hls/{segment}` | media:play |
| GET | `/images/{fileId}` | media:view |

## 9. 刮削

| 方法 | 路径 | 权限 |
|------|------|------|
| GET/POST/PUT/DELETE | `/scrape/schedules` | `library:edit`（写）/ `task:view`（读） |
| POST | `/scrape/schedules/{id}/runOnce` | `task:execute` 或 `library:edit` |
| GET | `/scrape/tasks` | task:view |
| POST | `/scrape/tasks` | `library:edit` 或 `task:execute`；body 可选 `libraryId`、`targetStatus` |
| POST | `/scrape/start` | 全库手动刮削；`library:edit` 或 `task:execute` |
| POST | `/scrape/start/{libraryId}` | 单库手动刮削；同上 |
| POST | `/scrape/tasks/{id}/cancel` | library:edit |

## 10. 搜索与 AI（Phase 3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/search` | FTS 关键词 |
| POST | `/search/semantic` | 向量语义（`scoredItems[].score`） |
| POST | `/search/query` | NL（响应含 `parsedFilters` + `results` 分页） |
| POST | `/search/reindex` | 重建 FTS+向量 |
| GET | `/ai/providers` | 已注册 AI Provider 列表（system:manage） |
| GET | `/ai/config` | 全局 AI 配置（system:manage） |
| PUT | `/ai/config` | 更新全局 AI 配置 |
| GET | `/ai/health` | Provider 健康 |
| GET | `/ai/suggestions` | 待审核列表 |
| POST | `/ai/suggestions/batch-approve` | 批量批准 |
| POST | `/ai/suggestions/batch-reject` | 批量拒绝 |
| POST | `/ai/suggestions/{id}/approve` | 批准 |
| POST | `/ai/suggestions/{id}/reject` | 拒绝 |
| GET | `/discover` | 推荐与继续观看 |

## 11. 插件配置（Phase 2）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/plugins` | 已注册插件列表 |
| GET | `/libraries/{id}` | 响应含 `plugins[]`；`extractors[]` **deprecated**（由 EXTRACTOR 插件派生） |
| GET | `/libraries/{id}/plugins` | 库级配置 |
| PUT | `/libraries/{id}/plugins` | 批量更新（`library:edit`） |
| POST | `/libraries/{id}/plugins/apply-default` | 按库 `type` 恢复默认插件链 |

### `plugins[]` 元素（GET library / GET library plugins）

```json
{
  "pluginId": "tmdb",
  "kind": "SCRAPER",
  "enabled": true,
  "priority": 100,
  "config": "{}"
}
```

| `kind` | 说明 |
|--------|------|
| EXTRACTOR | 扫描/refresh 本地链 |
| SCRAPER | 异步刮削任务 |
| CLASSIFIER | 库级分类（预留） |
| AI_PROVIDER | 库级 AI 覆盖 |

`extractors[]`（deprecated）：`{ "type": "NFO", "priority": 0, "enabled": true, "config": "{}" }`，不含 SCRAPER 行。

## 12. SSE

| 路径 | 权限 | 事件 |
|------|------|------|
| `GET /sse/events?clientId=` | `task:view` + 已登录 | scan.progress, scrape.task.updated, library.updated（以及少量 legacy） |
| `GET /system/logs/stream` | system:manage | log（系统日志 SSE） |

认证：与流式播放相同，浏览器 `EventSource` 可在查询参数传 `token=<accessToken>`（`JwtAuthenticationFilter` 支持）。不可匿名连接。

事件名、payload、legacy 弃用策略见 [`docs/v2/13-sse-event-dictionary.md`](docs/v2/13-sse-event-dictionary.md)。

## 13. 系统

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/system/status` | 公开（含 `setupCompleted`、`version`、`theme`） |
| GET | `/system/info` | media:view（统计按可访问库聚合；`totalUsers` 仅 ADMIN） |
| GET | `/system/capabilities` | media:view |
| GET/PUT | `/system/settings/summary` | system:manage（GET 概览） |
| GET/PUT | `/system/settings/security` | system:manage（`authEnabled`；**需重启**生效） |
| GET/PUT | `/system/settings/media-processing` | system:manage（`ffmpegPath`、`ffprobePath`） |
| GET/PUT | `/system/settings/integrations` | system:manage（`tmdbApiKey` 掩码） |
| GET/PUT | `/system/settings/appearance` | system:manage（`theme`: dark/light/system） |
| GET/PUT | `/system/config` | system:manage（**已废弃**；禁止写入 `ai.*`） |
| GET | `/system/logs/recent` | system:manage |
| GET | `/system/logs/stream` | system:manage |

Legacy 详细字段见 `docs/07-api-design.md`。
