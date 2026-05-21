# MediaManager v2 — 前端信息架构

## 1. 技术栈

- @umijs/max 4.x、Ant Design 5、暗色主题
- ahooks、@ant-design/pro-components
- xgplayer + hls（Phase 1）

## 2. 路由

| 路径 | 页面 | access | 说明 |
|------|------|--------|------|
| `/login` | Auth/Login | - | |
| `/setup` | Auth/Setup | - | |
| `/dashboard` | Dashboard | 登录 | 统计 |
| `/discover` | Discover | 登录 | Phase 4 推荐 |
| `/browse` | Media/Browse | media:view | Tab 筛选 |
| `/search` | Search | media:view | Phase 3 |
| `/libraries` | Library/List | library:view | |
| `/libraries/create` | Library/Create | library:create | |
| `/libraries/:id` | Library/Detail | library:view | |
| `/media/:id` | Media/Detail | media:view | |
| `/player/:id` | Media/Player | media:play | VideoPlayer |
| `/classification/tags` | Classification/Tags | tag:manage | |
| `/classification/categories` | Categories | category:manage | |
| `/users` | Users/Management | user:manage | |
| `/settings` | Settings/General | system:manage | |
| `/settings/rules` | Settings/Rules | category:manage | |
| `/settings/tasks` | Settings/Tasks | task:view | |
| `/settings/plugins` | Settings/Plugins | library:edit | Phase 2 |
| `/scrape/schedules` | Scrape/Schedules | library:edit | |
| `/system/logs` | System/Logs | system:manage | |
| `/intelligence/review` | Intelligence/Review | media:edit_metadata | Phase 3 |
| `/recycle-bin` | RecycleBin | media:view | Phase 1 |

## 3. access.ts

```typescript
export default function access(initialState: { currentUser?: API.CurrentUser }) {
  const perms = initialState?.currentUser?.permissions ?? [];
  const has = (p: string) => perms.includes(p);
  return {
    canManageSystem: has('system:manage'),
    canManageUsers: has('user:manage'),
    canManageLibrary: has('library:create') || has('library:edit'),
    canViewLibrary: has('library:view'),
    canViewMedia: has('media:view'),
    canPlayMedia: has('media:play'),
    canEditMetadata: has('media:edit_metadata'),
    canManageTags: has('tag:manage'),
    canManageCategories: has('category:manage'),
    canViewTasks: has('task:view'),
  };
}
```

路由配置 `access: 'canManageUsers'` 等与 umi 约定一致。

## 4. 核心组件（目标）

| 组件 | 状态 | 职责 |
|------|------|------|
| MediaCard | 已有 | 卡片展示 |
| FilterPanel | 内联 Browse | 抽离多维筛选 |
| VideoPlayer | Phase 1 | xgplayer HLS |
| MetadataEditor | Detail 内 Form | 元数据编辑 |
| TagSelect | 待建 | 多选标签 |
| ScanProgress | global SSE | 进度条 |
| IdentifyModal | Phase 1 | TMDb 选择 |
| PluginConfigForm | Phase 2 | Schema 动态表单 |

## 5. 服务层

`src/services/` 与 API 一一对应：`auth`, `library`, `media`, `stream`, `scrape`, `search`, `ai`, `classification`, `userActivity`, `system`。

## 6. 全局状态

- `models/user.ts`：当前用户与权限
- `models/global.ts`：SSE 连接、扫描/刮削进度

## 7. 请求

`app.ts`：Token 注入、401 跳转登录、setup 检测。
