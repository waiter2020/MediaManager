# MediaManager — 前端设计

## 1. 项目结构

```
media-manager-web/
├── config/
│   ├── config.ts              # umi 主配置 (路由/代理/主题)
│   └── proxy.ts               # 开发代理配置
├── src/
│   ├── app.ts                 # 运行时配置 (请求拦截/全局初始化)
│   ├── access.ts              # 权限配置
│   ├── global.css             # 全局样式变量
│   ├── constants/
│   │   ├── media.ts           # 媒体类型/状态/分辨率等枚举
│   │   └── permission.ts      # 权限常量
│   ├── layouts/
│   │   └── BasicLayout.tsx    # 主布局 (Pro Layout侧边栏+头部)
│   │
│   ├── pages/
│   │   ├── Auth/
│   │   │   ├── Login.tsx       # 登录页
│   │   │   └── Setup.tsx       # 首次安装向导
│   │   │
│   │   ├── Dashboard/
│   │   │   └── index.tsx       # 仪表盘 (统计卡片+最近添加+任务状态)
│   │   │
│   │   ├── Library/
│   │   │   ├── List.tsx        # 媒体库列表 (卡片式)
│   │   │   ├── Create.tsx      # 创建/编辑向导 (步骤表单)
│   │   │   └── Detail.tsx      # 媒体库详情+扫描控制
│   │   │
│   │   ├── Browse/
│   │   │   ├── index.tsx       # 统一浏览入口 (Tab: 全部/视频/图片/音频)
│   │   │   ├── VideoGrid.tsx   # 视频海报网格
│   │   │   ├── ImageGrid.tsx   # 图片瀑布流/网格
│   │   │   ├── AudioList.tsx   # 音频列表
│   │   │   └── FilterPanel.tsx # 多维度筛选面板
│   │   │
│   │   ├── Media/
│   │   │   ├── MovieDetail.tsx  # 电影详情 (海报+元数据+文件+标签)
│   │   │   ├── TvShowDetail.tsx # 剧集详情 (季/集列表)
│   │   │   ├── ImageDetail.tsx  # 图片详情 (EXIF+灯箱)
│   │   │   ├── AudioDetail.tsx  # 音频详情
│   │   │   └── Player.tsx       # 播放页面
│   │   │
│   │   ├── Tags/
│   │   │   └── Management.tsx  # 标签管理 (表格+批量操作)
│   │   │
│   │   ├── Categories/
│   │   │   └── Management.tsx  # 分类管理 (树形结构)
│   │   │
│   │   ├── Users/
│   │   │   └── Management.tsx  # 用户管理 (角色分配+库权限)
│   │   │
│   │   └── Settings/
│   │       ├── General.tsx     # 通用设置 (认证/TMDb API Key等)
│   │       ├── Tasks.tsx       # 后台任务 (扫描进度/历史)
│   │       └── Rules.tsx       # 分类规则管理
│   │
│   ├── services/              # API 请求层 (一一对应后端API)
│   │   ├── auth.ts
│   │   ├── user.ts
│   │   ├── library.ts
│   │   ├── media.ts
│   │   ├── tag.ts
│   │   ├── category.ts
│   │   ├── stream.ts
│   │   ├── classificationRule.ts
│   │   └── system.ts
│   │
│   ├── models/                # 全局状态 (umi useModel)
│   │   ├── user.ts            # 当前用户/权限
│   │   ├── library.ts         # 当前选中媒体库
│   │   └── global.ts          # 全局 SSE 事件/通知
│   │
│   └── components/            # 共享组件
│       ├── MediaCard/          # 海报卡片 (悬停显示标签/评分)
│       ├── MediaGrid/          # 可切换网格/列表模式
│       ├── FilterPanel/        # 多维筛选抽屉
│       ├── MetadataEditor/     # 元数据编辑 ProForm
│       ├── TagSelect/          # 标签选择器 (多选+颜色)
│       ├── VideoPlayer/        # xgplayer 封装
│       ├── ImageViewer/        # 图片灯箱 + 画廊
│       ├── AudioPlayer/        # 音频播放器
│       ├── CategoryTree/       # 树形分类选择
│       ├── ScanProgress/       # 扫描进度条 (SSE驱动)
│       └── ConfirmDelete/      # 删除确认弹窗 (源文件警告)
│
├── package.json
├── tsconfig.json
└── .umirc.ts
```

## 2. 路由设计

```typescript
export const routes = [
  { path: '/login', component: './Auth/Login', layout: false },
  { path: '/setup', component: './Auth/Setup', layout: false },
  {
    path: '/',
    component: '@/layouts/BasicLayout',
    routes: [
      { path: '/', redirect: '/dashboard' },
      { path: '/dashboard', component: './Dashboard' },

      // 媒体库
      { path: '/libraries', component: './Library/List' },
      { path: '/libraries/create', component: './Library/Create', access: 'canManageLibrary' },
      { path: '/libraries/:id', component: './Library/Detail' },
      { path: '/libraries/:id/edit', component: './Library/Create', access: 'canManageLibrary' },

      // 媒体浏览 (按类型区分)
      { path: '/browse', component: './Browse', name: '全部媒体' },
      { path: '/browse/videos', component: './Browse', name: '视频' },
      { path: '/browse/images', component: './Browse', name: '图片' },
      { path: '/browse/audio', component: './Browse', name: '音频' },

      // 媒体详情
      { path: '/media/:id', component: './Media/MovieDetail' },
      { path: '/media/:id/play', component: './Media/Player' },

      // 标签/分类
      { path: '/tags', component: './Tags/Management', access: 'canManageTags' },
      { path: '/categories', component: './Categories/Management', access: 'canManageCategories' },

      // 管理
      { path: '/users', component: './Users/Management', access: 'canManageUsers' },
      { path: '/settings', component: './Settings/General', access: 'canManageSystem' },
      { path: '/settings/tasks', component: './Settings/Tasks' },
      { path: '/settings/rules', component: './Settings/Rules', access: 'canManageCategories' },
    ],
  },
];
```

## 3. 权限控制 (access.ts)

```typescript
export default function access(initialState: { currentUser?: API.User }) {
  const { currentUser } = initialState;
  const perms = new Set(currentUser?.permissions || []);

  return {
    canManageSystem: perms.has('system:manage'),
    canManageUsers: perms.has('user:manage'),
    canManageLibrary: perms.has('library:create') || perms.has('library:edit'),
    canScanLibrary: perms.has('library:scan'),
    canEditMetadata: perms.has('media:edit_metadata'),
    canDeleteMedia: perms.has('media:delete'),
    canDeleteFile: perms.has('media:delete_file'),
    canManageTags: perms.has('tag:manage'),
    canAssignTags: perms.has('tag:assign'),
    canManageCategories: perms.has('category:manage'),
  };
}
```

## 4. 关键页面设计

### 4.1 仪表盘 (Dashboard)

```
┌─────────────────────────────────────────────────────────┐
│  Dashboard                                               │
│                                                          │
│  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐     │
│  │ 视频  │ │ 图片  │ │ 音频  │ │ 媒体库│ │ 标签  │     │
│  │ 1,234 │ │ 5,678 │ │  890  │ │   5   │ │  42   │     │
│  └───────┘ └───────┘ └───────┘ └───────┘ └───────┘     │
│                                                          │
│  最近添加                           扫描状态              │
│  ┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐        ┌──────────────────┐  │
│  │  ││  ││  ││  ││  ││  │        │ 电影库: 扫描中... │  │
│  │  ││  ││  ││  ││  ││  │        │ ████████░░ 80%   │  │
│  └──┘└──┘└──┘└──┘└──┘└──┘        │ 图片库: 完成      │  │
│                                    └──────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 4.2 媒体浏览页 (Browse)

```
┌─────────────────────────────────────────────────────────────┐
│  ┌───────┬───────┬───────┬───────┐                          │
│  │ 全部  │ 视频  │ 图片  │ 音频  │  ← Tab 切换类型          │
│  └───────┴───────┴───────┴───────┘                          │
│                                                              │
│  搜索: [__________________] [筛选🔽]  排序: [最近添加 ▼] 视图:[▦ ▤]│
│                                                              │
│  ┌── 筛选面板 (展开时) ──────────────────────────────────┐   │
│  │ 类型: [Action ▼] 年份: [2020-2024] 评分: [≥7.0]     │   │
│  │ 标签: [4K] [HDR] [HEVC] 分辨率: [全部 ▼]             │   │
│  │ 导演: [___] 演员: [___]  ← 按元数据标签筛选           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐        │
│  │    │ │    │ │    │ │    │ │    │ │    │ │    │        │
│  │海报│ │海报│ │海报│ │海报│ │海报│ │海报│ │海报│        │
│  │    │ │    │ │    │ │    │ │    │ │    │ │    │        │
│  │标题│ │标题│ │标题│ │标题│ │标题│ │标题│ │标题│        │
│  │⭐8.5│ │⭐7.2│ │⭐9.0│ │⭐6.8│ │⭐8.1│ │⭐7.5│ │⭐8.8│        │
│  └────┘ └────┘ └────┘ └────┘ └────┘ └────┘ └────┘        │
└─────────────────────────────────────────────────────────────┘
```

**图片浏览** 切换为瀑布流/网格布局:
```
┌─────────────────────────────────────────────┐
│  ┌──────┐ ┌────┐ ┌────────┐ ┌───┐ ┌──────┐ │
│  │      │ │    │ │        │ │   │ │      │ │
│  │      │ │    │ │        │ │   │ │      │ │
│  │      │ └────┘ │        │ │   │ │      │ │
│  └──────┘ ┌────┐ └────────┘ └───┘ └──────┘ │
│  ┌────┐   │    │ ┌──────┐ ┌────────┐       │
│  │    │   │    │ │      │ │        │       │
│  └────┘   └────┘ │      │ │        │       │
│                   └──────┘ └────────┘       │
└─────────────────────────────────────────────┘
```

**音频浏览** 显示为列表:
```
┌────────────────────────────────────────────────────────┐
│  #  │ 标题           │ 艺术家     │ 专辑     │ 时长    │
│  1  │ Song Name      │ Artist     │ Album    │ 3:45   │
│  2  │ Another Song   │ Artist 2   │ Album 2  │ 4:12   │
│  ▶  │ 迷你播放器条                                     │
└────────────────────────────────────────────────────────┘
```

### 4.3 媒体详情页 (MovieDetail)

```
┌─────────────────────────────────────────────────────────┐
│  ┌── 背景图 (模糊) ──────────────────────────────────┐  │
│  │  ┌──────┐                                         │  │
│  │  │      │  Inception (2010)                       │  │
│  │  │ 海报 │  ⭐ 8.8  │  PG-13  │  148分钟           │  │
│  │  │      │                                         │  │
│  │  │      │  A thief who steals corporate...        │  │
│  │  │      │                                         │  │
│  │  │      │  [▶ 播放] [🔄 刷新] [✏️ 编辑] [🗑️ 删除] │  │
│  │  └──────┘                                         │  │
│  │  标签: [Sci-Fi] [4K] [HEVC] [HDR] [+ 添加]       │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  演员: Christopher Nolan (导) │ Leonardo DiCaprio │ ...  │
│                                                          │
│  文件信息                                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Inception.2010.2160p.UHD.BluRay.x265.mkv         │   │
│  │ 4K (3840×2160) │ HEVC │ 48.2 GB │ AAC 5.1       │   │
│  │ [▶ 播放] [🗑️ 删除文件]                            │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 4.4 创建媒体库向导

步骤式表单 (Ant Design Steps + ProForm):

```
步骤 1: 基本信息        步骤 2: 目录配置      步骤 3: 提取器配置
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ 名称: [       ]  │  │ + 添加目录       │  │ ☑ NFO (优先级 0) │
│ 类型: [电影 ▼]   │  │ /media/movies    │  │ ☑ FFprobe (1)    │
│ 语言: [中文 ▼]   │  │ /media/movies-2  │  │ ☑ TMDb (2)       │
│ 自动扫描: [✅]   │  │                  │  │   API Key: [___] │
│ 间隔: [30分钟]   │  │ [📂 选择目录]    │  │ ☐ EXIF (3)       │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

## 5. 主题与样式

- **暗色主题优先**: 媒体管理天然适合暗色背景
- **Ant Design 5 CSS-in-JS token 定制**
- **响应式**: 适配桌面 / 平板 / 手机
- **动效**: 卡片 Hover 放大, 页面切换过渡, 骨架屏加载

### 主色调

```css
:root {
  --primary: #1668dc;        /* Ant Design 蓝 */
  --bg-primary: #141414;     /* 暗色主背景 */
  --bg-secondary: #1f1f1f;   /* 卡片背景 */
  --bg-elevated: #2a2a2a;    /* 悬浮元素 */
  --text-primary: #ffffffd9; /* 主文字 */
  --text-secondary: #ffffff73;/* 次要文字 */
  --accent-green: #52c41a;   /* 成功 */
  --accent-red: #ff4d4f;     /* 危险/删除 */
}
```

## 6. 实时通信 (SSE)

前端通过 SSE 接收后端实时事件：

```typescript
// models/global.ts
const eventSource = new EventSource('/api/v1/system/events');

eventSource.addEventListener('scan_progress', (e) => {
  const data = JSON.parse(e.data);
  // { libraryId: 1, total: 500, scanned: 250, status: 'SCANNING' }
  updateScanProgress(data);
});

eventSource.addEventListener('media_added', (e) => {
  // 通知刷新列表
  notification.info({ message: `新增媒体: ${data.title}` });
});

eventSource.addEventListener('task_completed', (e) => {
  message.success(`任务完成: ${data.taskName}`);
});
```

## 7. 请求层配置

```typescript
// app.ts - 请求拦截器
export const request = {
  baseURL: '/api/v1',
  timeout: 30000,
  requestInterceptors: [
    (config) => {
      const token = localStorage.getItem('accessToken');
      if (token) config.headers.Authorization = `Bearer ${token}`;
      return config;
    },
  ],
  responseInterceptors: [
    (response) => {
      if (response.data?.code === 40101) {
        // Token 过期 → 尝试刷新
        return refreshTokenAndRetry(response.config);
      }
      return response;
    },
  ],
};
```
