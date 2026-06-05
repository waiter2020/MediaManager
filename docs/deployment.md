# MediaManager 部署指南

## 1. 要求

| 组件 | 版本 |
|------|------|
| Docker Desktop / Engine | 20.10+ |
| Docker Compose | v2 |
| 磁盘 | 数据卷 + 媒体只读卷 |
| FFmpeg | 镜像内已含（容器路径 `/usr/bin/ffmpeg`） |

## 2. 快速启动

```powershell
$env:JWT_SECRET="change-me-in-production-256-bit-key"
$env:HOST_MEDIA_PATH="E:\Movies"
docker-compose up --build -d
```

- Web: http://localhost/
- 健康检查: http://localhost/api/v1/system/status

## 3. 环境变量

| 变量 | 说明 | 默认 |
|------|------|------|
| `JWT_SECRET` / `MEDIAMANAGER_AUTH_JWT_SECRET` | JWT 密钥 | 必须修改生产 |
| `HOST_MEDIA_PATH` | 宿主机媒体目录 | 映射到 `/home/media` |
| `MEDIAMANAGER_STORAGE_PATH_MAP_FROM` | DB 中旧路径前缀 | 同 HOST_MEDIA_PATH |
| `MEDIAMANAGER_STORAGE_PATH_MAP_TO` | 容器内前缀 | `/home/media` |
| `MEDIAMANAGER_FFMPEG_PATH` | FFmpeg | `/usr/bin/ffmpeg` |
| `MEDIAMANAGER_AUTH_ENABLED` | 是否启用认证 | `true` |

数据目录：卷 `./data` → `/app/data`（SQLite、`cache/`）。

## 4. 路径映射（Windows）

数据库若保存 `E:\Movies\film.mkv`，容器需：

```yaml
environment:
  MEDIAMANAGER_STORAGE_PATH_MAP_FROM: E:\Movies
  MEDIAMANAGER_STORAGE_PATH_MAP_TO: /home/media
volumes:
  - E:\Movies:/home/media:ro
```

否则流式播放返回 40404。

## 5. 构建镜像源（国内加速）

项目**默认按国内网络**配置：基础镜像走 DaoCloud 缓存、包管理器走阿里云 / npmmirror、Maven 走阿里云仓库。

| 层级 | 国内默认 | 配置 |
|------|----------|------|
| Docker 基础镜像 | `docker.m.daocloud.io/library/...` | `Dockerfile` / `docker-compose.yml` / `.env.example` |
| Alpine apk | `mirrors.tuna.tsinghua.edu.cn`（main+community） | `MIRROR_PROFILE=cn`，`ALPINE_MIRROR_HOST` |
| Debian/Ubuntu apt（Maven 阶段） | `mirrors.aliyun.com` | `MIRROR_PROFILE=cn` |
| npm 包元数据 | `registry.npmjs.org`（可 `.env` 改 `NPM_REGISTRY`） | `Dockerfile` `NPM_CONFIG_REGISTRY` |
| npm 二进制 | `npmmirror.com/mirrors/*` | `media-manager-web/.npmrc` |
| Maven | 阿里云 central / spring / public | 根目录 `settings.xml` |
| BuildKit 层缓存 | `node_modules` + `/root/.npm` + Maven `.m2` | `Dockerfile` `RUN --mount=type=cache` |
| npm 二进制 | `esbuild_binary_host` 等 | `media-manager-web/.npmrc` |

### 一键配置

```powershell
copy .env.example .env
# 可选：再配置宿主机 Docker Engine，见 docker/daemon.json.example
docker compose build
```

`.env` 中已默认 `DOCKER_BUILDKIT=1` 与 DaoCloud 镜像全名，一般**无需再改** `NODE_IMAGE` 等变量。

### 构建参数（`.env` 或环境变量）

| 变量 | 国内默认 | 说明 |
|------|----------|------|
| `MIRROR_PROFILE` | `cn` | `cn` 启用 apk/apt/npm 国内源；`default` 用上游 |
| `DOCKER_HUB_MIRROR` | `docker.m.daocloud.io` | 与下面三个镜像前缀一致 |
| `NODE_IMAGE` | `docker.m.daocloud.io/library/node:20-alpine` | 避免直连 Docker Hub（Umi 依赖 Node 20+） |
| `MAVEN_IMAGE` | `docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21` | 同上 |
| `JRE_IMAGE` | `docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine` | 同上 |
| `MAVEN_SETTINGS_FILE` | `settings.xml` | 海外改为 `settings-default.xml` |
| `ALPINE_MIRROR_HOST` | `mirrors.tuna.tsinghua.edu.cn` | apk 慢时可换 `mirrors.ustc.edu.cn` 等 |

海外示例（写入 `.env` 或临时 export）：

```powershell
$env:MIRROR_PROFILE="default"
$env:MAVEN_SETTINGS_FILE="settings-default.xml"
$env:NODE_IMAGE="node:20-alpine"
$env:MAVEN_IMAGE="maven:3.9-eclipse-temurin-21"
$env:JRE_IMAGE="eclipse-temurin:21-jre-alpine"
docker compose build --no-cache
```

### 宿主机 Docker Hub 加速（建议）

即使 Dockerfile 已写 DaoCloud 全名，仍建议在 **Docker Desktop / daemon** 配置 `registry-mirrors`，加速其它 `docker pull` 与 BuildKit 语法镜像。

仓库提供模板：`docker/daemon.json.example`（复制到 Docker Engine JSON 或 Linux `/etc/docker/daemon.json` 后重启 Docker）。

常用镜像站：`https://docker.m.daocloud.io`、`https://docker.1ms.run`（以本机可用为准）。

### 前端构建说明

Docker 内使用 **`npm ci` + `package-lock.json`**（可复现、利于缓存），不再依赖不存在的 `pnpm-lock.yaml`。本地开发仍可用 `npm install` / `pnpm`，`.npmrc` 已指向 npmmirror。

## 6. 本地开发

### 后端

```powershell
cd media-manager-server
mvn -s ../settings.xml spring-boot:run
```

`media-manager-server/.mvn/maven.config` 已默认指向根目录 `settings.xml`（阿里云镜像）。

### 前端

```powershell
cd media-manager-web
npm install
npm run dev
```

`media-manager-web/.npmrc` 已指向 `registry.npmmirror.com`。海外开发可临时删除或改回 `https://registry.npmjs.org`。

代理：`/api/v1` → `http://localhost:8080`（见 `.umirc.ts`）。

## 7. AI 本地（Phase 3，可选）

Ollama 运行于宿主机：

```powershell
ollama pull nomic-embed-text
ollama pull qwen2.5:7b
```

容器访问宿主机（`docker-compose.yml` 已默认 `http://host.docker.internal:11434`）：

```yaml
environment:
  MEDIAMANAGER_AI_OLLAMA_BASE_URL: http://host.docker.internal:11434
extra_hosts:
  - "host.docker.internal:host-gateway"
```

**注意**：在 **设置 → AI** 中若仍保存 `http://localhost:11434`，在容器内会指向容器自身而非宿主机，嵌入请求会 `Connection refused`。请改为 `http://host.docker.internal:11434`，或留空以使用环境变量（V19 迁移会清空数据库中的 localhost 种子值）。FFmpeg 路径在 **设置 → 媒体处理** 中配置。

库级配置见 `docs/v2/04-ai-platform.md`。

## 8. 日志

```powershell
docker-compose logs -f mediamanager
```

系统日志 SSE：登录后访问「系统日志」菜单（需 `system:manage`）。

## 9. 备份

- 停止容器后备份 `./data/mediamanager.db` 与 `./data/cache/`。
- 恢复时保持 Flyway 版本一致。

## 10. 生产检查清单

- [ ] 修改 JWT 密钥
- [ ] 限制 80/443 访问
- [ ] 媒体卷只读
- [ ] 审查 `library_access` 与角色
- [ ] 敏感库关闭 AI outbound（Phase 3）
- [ ] 定期备份 `data/`

## 11. 文档

- 架构：[v2/01-architecture.md](./v2/01-architecture.md)
- 实现计划：[v2/11-implementation-plan.md](./v2/11-implementation-plan.md)
