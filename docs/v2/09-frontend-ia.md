# MediaManager v2 — 前端信息架构

## 1. 技术栈

- @umijs/max 4.x、Ant Design 5、暗色主题（支持全局 `ui.theme`）
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
| `/libraries/:id/edit` | Library/Create | canManageLibrary | 编辑 |
| `/libraries/:id/plugins` | Library/Plugins | canEditLibraryPlugins | 库级插件 |
| `/libraries/create` | Library/Create | canManageLibrary | 三步创建向导 |
| `/media/:id` | Media/Detail | media:view | |
| `/player/:id` | Media/Player | media:play | VideoPlayer |
| `/classification/tags` | Classification/Tags | tag:manage | |
| `/classification/categories` | Categories | category:manage | |
| `/settings` | Settings/_layout | canAccessSettings | 侧栏 Tab 设置中心 |
| `/settings/general` | Settings/General | system:manage | 概览 |
| `/settings/security` | Settings/Security | system:manage | |
| `/settings/media-processing` | Settings/MediaProcessing | system:manage | |
| `/settings/integrations` | Settings/Integrations | system:manage | |
| `/settings/appearance` | Settings/Appearance | system:manage | |
| `/settings/ai` | Settings/Ai | system:manage | |
| `/settings/rules` | Settings/Rules | category:manage | |
| `/settings/tasks` | Settings/Tasks | task:view | |
| `/settings/users` | Users/Management | user:manage | |
| `/settings/logs` | System/Logs | system:manage | |
| `/settings/scrape-schedules` | Scrape/Schedules | canManageLibrary | |
| `/settings/plugins` | Settings/Plugins | canEditLibraryPlugins | 注册表 |
| `/settings/profile` | Settings/Profile | 登录 | hideInMenu |
| `/users` | redirect → `/settings/users` | | 兼容旧书签 |
| `/system/logs` | redirect → `/settings/logs` | | |
| `/scrape/schedules` | redirect → `/settings/scrape-schedules` | | |
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
    canEditLibraryPlugins: has('library:edit'),
    canAccessSettings:
      has('system:manage') ||
      has('user:manage') ||
      has('category:manage') ||
      has('task:view') ||
      has('library:create') ||
      has('library:edit'),
  };
}
```

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
| PluginExtractorConfigForm | 已有 | 库级提取器表单 |

## 5. 服务层

`src/services/` 与 API 一一对应：`auth`, `library`, `media`, `stream`, `scrape`, `search`, `ai`, `classification`, `userActivity`, `system`, **`settings`**（Typed 系统配置）。

## 6. 全局状态

- `@@initialState`：当前用户、权限、**theme**
- `models/global.ts`：SSE 连接、扫描/刮削进度

## 7. 请求

`app.ts`：Token 注入、401 跳转登录、setup 检测、启动时 `GET /system/status` 应用全局主题。
