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

## 6. 媒体项

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/items` | media:view + 库过滤 |
| GET | `/items/filters` | media:view |
| GET | `/items/{id}` | media:view |
| GET | `/items/{id}/detail` | media:view |
| PUT | `/items/{id}/metadata` | media:edit_metadata |
| POST | `/items/{id}/refresh` | media:refresh |
| POST | `/items/{id}/identify` | media:edit_metadata |
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
| GET/POST/PUT/DELETE | `/scrape/schedules` | system:manage 或 library:edit |
| GET | `/scrape/tasks` | task:view |
| POST | `/scrape/tasks` | library:edit |
| POST | `/scrape/tasks/{id}/cancel` | library:edit |

## 10. 搜索与 AI（Phase 3）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/search` | FTS 关键词 |
| POST | `/search/semantic` | 向量语义 |
| POST | `/search/query` | NL 组合 |
| GET | `/ai/suggestions` | 待审核列表 |
| POST | `/ai/suggestions/{id}/approve` | 批准 |
| POST | `/ai/suggestions/{id}/reject` | 拒绝 |
| GET | `/discover` | 推荐与继续观看 |

## 11. 插件配置（Phase 2）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/plugins` | 已注册插件列表 |
| GET | `/libraries/{id}/plugins` | 库级配置 |
| PUT | `/libraries/{id}/plugins` | 批量更新 |

## 12. SSE

| 路径 | 事件 |
|------|------|
| `GET /sse/events?clientId=` | scan.progress, scrape.task.updated, library.updated |

## 13. 系统

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/system/status` | 公开 |
| GET | `/system/logs/stream` | system:manage |

Legacy 详细字段见 `docs/07-api-design.md`。
