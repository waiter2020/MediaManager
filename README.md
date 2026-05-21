# MediaManager

自托管现代化媒体管理平台。设计文档（v2）：[`docs/v2/00-vision-and-roadmap.md`](docs/v2/00-vision-and-roadmap.md) · 部署：[`docs/deployment.md`](docs/deployment.md) · 实现计划：[`docs/v2/11-implementation-plan.md`](docs/v2/11-implementation-plan.md)

> Legacy 设计（`docs/01-09`）仅供参考，以 v2 为准。

## 一键启动（Docker Desktop / Windows）

### 1) 准备环境变量（可选）

- **JWT_SECRET**：JWT 密钥（生产务必修改）
- **HOST_MEDIA_PATH**：宿主机媒体目录（Windows 示例：`E:\Movies`）

PowerShell 示例：

```powershell
$env:JWT_SECRET="change-me-in-production-256-bit-key"
$env:HOST_MEDIA_PATH="E:\Movies"
```

> 说明：容器内会把 `HOST_MEDIA_PATH` 自动映射到 `/home/media`，用于兼容数据库里已保存的 Windows 绝对路径（避免出现 `40404 Media file not found`）。

### 2) 启动

```powershell
docker-compose up --build -d
```

### 3) 访问

- Web：`http://localhost/`
- 健康检查：`http://localhost/api/v1/system/status`

### 4) 查看日志

```powershell
docker-compose logs -f
```

## 关键功能入口

- **初始化/登录**：首次启动会提示 `/setup` 创建管理员账号，之后 `/login` 登录
- **刮削计划**：侧边栏菜单「刮削计划」(`/scrape/schedules`)

