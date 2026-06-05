# MediaManager — REST API 设计

## 1. 通用规范

### 1.1 基本约定

- **Base URL**: `/api/v1`
- **Content-Type**: `application/json`
- **认证**: `Authorization: Bearer <accessToken>` (可配置关闭)
- **分页**: `page` (从1开始), `size` (默认20, 最大100)
- **排序**: `sort=field:asc|desc` (多字段用逗号分隔)

### 1.2 统一响应格式

```json
// 成功
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-03-11T16:00:00Z"
}

// 分页
{
  "code": 200,
  "data": {
    "items": [...],
    "total": 150,
    "page": 1,
    "size": 20,
    "totalPages": 8
  }
}

// 错误
{
  "code": 40001,
  "message": "Validation failed",
  "errors": [
    { "field": "name", "message": "不能为空" }
  ],
  "timestamp": "2026-03-11T16:00:00Z"
}
```

### 1.3 错误码设计

| 范围 | 分类 | 示例 |
|------|------|------|
| 200 | 成功 | |
| 400xx | 参数校验错误 | 40001 字段校验失败 |
| 401xx | 认证错误 | 40101 Token 过期, 40102 无效 Token |
| 403xx | 权限错误 | 40301 无权限, 40302 无库访问权限 |
| 404xx | 资源不存在 | 40401 媒体项不存在 |
| 500xx | 服务端错误 | 50001 内部错误 |

---

## 2. 认证接口

### POST /api/v1/auth/login
登录获取 Token。

**Request**:
```json
{ "username": "admin", "password": "xxx" }
```

**Response**:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "xxx",
  "expiresIn": 7200,
  "user": {
    "id": 1, "username": "admin", "displayName": "管理员",
    "roles": ["SUPER_ADMIN"],
    "permissions": ["system:manage", "user:manage", ...]
  }
}
```

### POST /api/v1/auth/refresh
刷新 Token。

### POST /api/v1/auth/logout
登出 (撤销 Refresh Token)。

### POST /api/v1/auth/setup
首次安装创建超管账户 (仅无用户时可调用)。

---

## 3. 用户管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/users` | `user:manage` | 用户列表 |
| POST | `/api/v1/users` | `user:manage` | 创建用户 |
| GET | `/api/v1/users/{id}` | `user:manage` | 用户详情 |
| PUT | `/api/v1/users/{id}` | `user:manage` | 更新用户 |
| DELETE | `/api/v1/users/{id}` | `user:manage` | 删除用户 |
| PUT | `/api/v1/users/{id}/roles` | `user:manage` | 分配角色 |
| PUT | `/api/v1/users/{id}/library-access` | `user:manage` | 配置库权限 |
| GET | `/api/v1/users/me` | 已登录 | 当前用户信息 |
| PUT | `/api/v1/users/me` | 已登录 | 更新个人信息 |
| PUT | `/api/v1/users/me/password` | 已登录 | 修改密码 |

---

## 4. 媒体库管理

### POST /api/v1/libraries
创建媒体库。权限: `library:create`

```json
{
  "name": "我的电影",
  "type": "MOVIE",
  "language": "zh",
  "autoScan": true,
  "scanIntervalMinutes": 30,
  "paths": [
    { "path": "/media/movies", "priority": 0 },
    { "path": "/media/movies-2", "priority": 1 }
  ],
  "extractors": [
    { "type": "NFO", "priority": 0, "enabled": true },
    { "type": "FFPROBE", "priority": 1, "enabled": true },
    { "type": "TMDB", "priority": 2, "enabled": true, "config": { "apiKey": "xxx", "language": "zh-CN" } }
  ]
}
```

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/libraries` | `library:view` | 列出所有媒体库 (含统计) |
| GET | `/api/v1/libraries/{id}` | `library:view` | 媒体库详情 |
| PUT | `/api/v1/libraries/{id}` | `library:edit` | 更新配置 |
| DELETE | `/api/v1/libraries/{id}` | `library:delete` | 删除媒体库 (仅清DB) |
| POST | `/api/v1/libraries/{id}/scan` | `library:scan` | 触发扫描 |
| GET | `/api/v1/libraries/{id}/stats` | `library:view` | 统计信息 |

---

## 5. 媒体项

### GET /api/v1/items
多维度筛选查询。权限: `media:view`

**Query Parameters**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `libraryId` | Long | 按媒体库筛选 |
| `type` | String | MOVIE / TV_SHOW / IMAGE / AUDIO |
| `status` | String | IDENTIFIED / UNIDENTIFIED / ERROR |
| `tags` | String | 标签名，逗号分隔 |
| `categories` | String | 分类路径，如 `GENRE/Action,YEAR/2024` |
| `genre` | String | 按类型标签 |
| `actor` | String | 按演员 |
| `director` | String | 按导演 |
| `studio` | String | 按制片公司 |
| `resolution` | String | 4K / 1080p / 720p |
| `codec` | String | HEVC / AVC / AV1 |
| `yearFrom` / `yearTo` | Int | 年份范围 |
| `ratingMin` / `ratingMax` | Float | 评分范围 |
| `camera` | String | 相机品牌 (图片) |
| `artist` | String | 艺术家 (音频) |
| `album` | String | 专辑 (音频) |
| `q` | String | 全文搜索 (标题/简介) |
| `sort` | String | `release_date:desc`, `title:asc`, `created_at:desc` |
| `page` / `size` | Int | 分页 |

**Response**:
```json
{
  "items": [
    {
      "id": 1,
      "title": "Inception",
      "originalTitle": "Inception",
      "type": "MOVIE",
      "status": "IDENTIFIED",
      "releaseDate": "2010-07-16",
      "rating": 8.8,
      "overview": "A thief who...",
      "posterUrl": "/api/v1/items/1/poster",
      "tags": [{"id": 1, "name": "Sci-Fi", "color": "#3498db"}],
      "categories": [{"type": "GENRE", "name": "Sci-Fi"}, {"type": "YEAR", "name": "2010"}],
      "files": [{"id": 10, "fileName": "Inception.mkv", "resolution": "1080p", "codec": "HEVC"}]
    }
  ],
  "total": 150, "page": 1, "size": 20
}
```

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/items/{id}` | `media:view` | 详情 (含完整元数据) |
| PUT | `/api/v1/items/{id}/metadata` | `media:edit_metadata` | 编辑元数据 |
| POST | `/api/v1/items/{id}/refresh` | `media:refresh` | 刷新元数据 |
| DELETE | `/api/v1/items/{id}` | `media:delete` | 删除记录 (不删源文件) |
| DELETE | `/api/v1/items/{id}/file` | `media:delete_file` | 删除源文件 (二次确认) |
| POST | `/api/v1/items/{id}/identify` | `media:edit_metadata` | 手动匹配 (选择TMDb结果) |
| GET | `/api/v1/items/{id}/poster` | `media:view` | 海报图片 |
| GET | `/api/v1/items/{id}/backdrop` | `media:view` | 背景图 |

### GET /api/v1/items/filters
获取可用的筛选选项（动态聚合）。

```json
{
  "types": ["MOVIE", "TV_SHOW", "IMAGE", "AUDIO"],
  "genres": [{"name": "Action", "count": 42}, ...],
  "years": [{"name": "2024", "count": 15}, ...],
  "resolutions": [{"name": "4K", "count": 8}, ...],
  "codecs": [{"name": "HEVC", "count": 30}, ...],
  "tags": [{"id": 1, "name": "Sci-Fi", "color": "#3498db", "count": 20}, ...],
  "cameras": ["Canon", "Sony"],
  "artists": ["Artist1", "Artist2"]
}
```

---

## 6. 标签管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/tags` | `media:view` | 标签列表 (含使用计数) |
| POST | `/api/v1/tags` | `tag:manage` | 创建标签 |
| PUT | `/api/v1/tags/{id}` | `tag:manage` | 编辑标签 |
| DELETE | `/api/v1/tags/{id}` | `tag:manage` | 删除标签 |
| POST | `/api/v1/items/{id}/tags` | `tag:assign` | 打标 `{"tagIds": [1,2,3]}` |
| DELETE | `/api/v1/items/{id}/tags/{tagId}` | `tag:assign` | 移除标签 |
| POST | `/api/v1/items/batch/tags` | `tag:assign` | 批量打标 `{"itemIds":[...], "tagIds":[...]}` |

---

## 7. 分类管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/categories` | `media:view` | 分类树 (含计数) |
| POST | `/api/v1/categories` | `category:manage` | 创建分类 |
| PUT | `/api/v1/categories/{id}` | `category:manage` | 编辑分类 |
| DELETE | `/api/v1/categories/{id}` | `category:manage` | 删除分类 |

---

## 8. 点播

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/stream/{fileId}` | `media:play` | 视频/音频流 (Range) |
| GET | `/api/v1/stream/{fileId}/hls/master.m3u8` | `media:play` | HLS 播放列表 |
| GET | `/api/v1/stream/{fileId}/hls/{seg}.ts` | `media:play` | HLS 分片 |
| GET | `/api/v1/images/{fileId}` | `media:play` | 原图 |
| GET | `/api/v1/images/{fileId}?w=300` | `media:play` | 缩略图 |

---

## 9. 系统管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/system/config` | `system:manage` | 系统配置 |
| PUT | `/api/v1/system/config` | `system:manage` | 更新配置 |
| GET | `/api/v1/system/tasks` | `task:view` | 后台任务列表 |
| DELETE | `/api/v1/system/tasks/{id}` | `system:manage` | 取消任务 |
| GET | `/api/v1/system/events` | 已登录 | SSE 实时事件流 |
| GET | `/api/v1/system/info` | 已登录 | 系统信息/版本 |
| GET | `/api/v1/system/status` | 公开 | 系统是否需要初始化 |

---

## 10. 分类规则管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/classification-rules` | `category:manage` | 规则列表 |
| POST | `/api/v1/classification-rules` | `category:manage` | 创建规则 |
| PUT | `/api/v1/classification-rules/{id}` | `category:manage` | 编辑规则 |
| DELETE | `/api/v1/classification-rules/{id}` | `category:manage` | 删除规则 |
| POST | `/api/v1/classification-rules/{id}/execute` | `category:manage` | 对存量数据执行规则 |
