# MediaManager — 总体架构设计

## 1. 项目概述

MediaManager 是一个自托管的媒体管理平台，参考 Jellyfin、Plex、Emby 的设计理念，提供多媒体库管理、元数据提取、自动分类打标、文件监听、媒体点播能力。

### 1.1 设计目标

| 目标 | 说明 |
|------|------|
| **轻量化** | 仅依赖 SQLite，无 Redis 等额外中间件，单容器部署 |
| **非侵入** | 默认不修改源文件；仅用户在管理界面主动删除时才删除源文件 |
| **插件化** | 元数据提取器、分类器可插拔，按媒体库独立配置 |
| **高性能** | Spring Boot 4 Virtual Threads，天然高并发 I/O |
| **标准兼容** | 完全兼容 Jellyfin/Kodi NFO 元数据格式 |

---

## 2. 系统架构图

```
┌─────────────────────── 用户层 ─────────────────────────┐
│  浏览器 (umi-max + Ant Design 5)                       │
│  Dashboard │ 媒体库 │ 浏览(视频/图片/音频) │ 设置      │
└──────────────────────┬─────────────────────────────────┘
                       │ REST / SSE
┌──────────────────────┴─────────────────────────────────┐
│  Spring Boot 3 (Java 21) + 前端静态资源                 │
│  ┌────────────────────── 业务层 ─────────────────────┐  │
│  │ Library │ Media │ Metadata │ Classifier │ Stream  │  │
│  │ Service │Service│ Pipeline │ Engine     │ Service │  │
│  │         │       │          │            │         │  │
│  │ FileWatcher Service │ Auth Service               │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────── 基础设施 ─────────────────────┐  │
│  │ JPA │ Security │ Events │ Caffeine │ Scheduler   │  │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────┬─────────────────────────────────┘
                       │
  SQLite (嵌入式数据库文件) │ FFmpeg │ 文件系统(媒体源)
```

---

## 3. 技术选型

### 3.1 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.x | 主框架 (Java 21, Virtual Threads) |
| Spring Data JPA | - | ORM / 数据访问 |
| SQLite | 3.x | 嵌入式数据库文件存储 |
| Caffeine | 3.x | 进程内缓存，无需额外服务 |
| Flyway | - | 数据库迁移，初始化角色/权限/配置 |
| Spring Security + JWT | - | 认证鉴权 |
| MapStruct | - | DTO ↔ Entity 映射 |
| SpringDoc OpenAPI | - | API 文档 |
| FFmpeg | - | 视频探测/转码/缩略图 |
| metadata-extractor | - | 图片 EXIF 读取（预留接入） |
| Maven | 3.9.x | 构建工具 |

### 3.2 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| @umijs/max | 4.x | 企业级前端框架 |
| Ant Design | 5.x | UI 组件库 |
| @ant-design/pro-components | 2.x | ProTable/ProForm |
| xgplayer | 3.x | 视频播放器 |
| react-photo-album | - | 图片网格浏览 |
| ahooks | - | React Hooks 库 |

---

## 4. 角色与权限设计 (RBAC)

### 4.1 模型

```
User ──N:N──→ Role ──N:N──→ Permission
User ──N:N──→ LibraryAccess (媒体库级权限)
```

### 4.2 预置角色

| 角色 | 标识 | 说明 |
|------|------|------|
| 超级管理员 | `SUPER_ADMIN` | 所有权限，不可删除，初始化时创建 |
| 管理员 | `ADMIN` | 管理媒体库/用户/设置 |
| 普通用户 | `USER` | 浏览/播放/编辑元数据标签 |
| 访客 | `GUEST` | 仅浏览和播放 |

### 4.3 权限清单

| 权限标识 | 说明 | SA | A | U | G |
|----------|------|:--:|:-:|:-:|:-:|
| `system:manage` | 系统设置 | ✅ | ✅ | ❌ | ❌ |
| `user:manage` | 用户管理 | ✅ | ✅ | ❌ | ❌ |
| `user:manage_admin` | 管理管理员 | ✅ | ❌ | ❌ | ❌ |
| `library:create` | 创建媒体库 | ✅ | ✅ | ❌ | ❌ |
| `library:edit` | 编辑媒体库 | ✅ | ✅ | ❌ | ❌ |
| `library:delete` | 删除媒体库 | ✅ | ✅ | ❌ | ❌ |
| `library:scan` | 触发扫描 | ✅ | ✅ | ✅ | ❌ |
| `library:view` | 查看媒体库 | ✅ | ✅ | ✅ | ✅ |
| `media:view` | 浏览媒体 | ✅ | ✅ | ✅ | ✅ |
| `media:play` | 播放媒体 | ✅ | ✅ | ✅ | ✅ |
| `media:edit_metadata` | 编辑元数据 | ✅ | ✅ | ✅ | ❌ |
| `media:delete` | 删除记录 | ✅ | ✅ | ❌ | ❌ |
| `media:delete_file` | 删除源文件 | ✅ | ✅ | ❌ | ❌ |
| `media:refresh` | 刷新元数据 | ✅ | ✅ | ✅ | ❌ |
| `tag:manage` | 管理标签 | ✅ | ✅ | ✅ | ❌ |
| `tag:assign` | 打标/取消 | ✅ | ✅ | ✅ | ❌ |
| `category:manage` | 管理分类 | ✅ | ✅ | ❌ | ❌ |
| `task:view` | 查看任务 | ✅ | ✅ | ✅ | ❌ |

### 4.4 媒体库级权限

管理员可为用户配置特定媒体库的访问权限，覆盖全局角色（取更严格者）。

### 4.5 认证流程

- JWT: Access Token (2h) + Refresh Token (7d, 存DB可撤销)
- 首次安装：引导创建超级管理员
- 支持 `auth.enabled=false` 跳过认证

---

## 5. 设计模式

- **事件驱动**：文件变更 → Spring Event → 异步元数据提取/分类
- **管线模式**：元数据提取器按优先级链式执行
- **策略模式**：分类器接口统一，多维度实现
- **观察者模式**：`@EventListener` + `@Async`

---

## 6. 部署架构

### 6.1 单容器部署（推荐）

前端打包到 Spring Boot JAR 的 `static/` 目录，单端口 8080 提供所有服务，SQLite 数据文件存放在挂载目录 `./data` 中，使用 Maven 多阶段 Docker 构建（详见仓库根目录 `Dockerfile` 与 `docker-compose.yml`）。
