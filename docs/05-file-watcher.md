# MediaManager — 文件监听服务

## 1. 设计概述

FileWatcher 服务负责实时监听所有媒体库目录的文件系统变更，自动触发元数据提取、数据库更新或记录清理。

## 2. 架构

```
┌─────────────────────────────────────────────────┐
│                FileWatcherService                │
│                                                  │
│  ┌──────────────────┐   ┌─────────────────────┐  │
│  │ DirectoryWatcher │   │ ScheduledScanner    │  │
│  │ (实时监听)        │   │ (定时全量扫描)       │  │
│  │ Java WatchService│   │ Spring @Scheduled   │  │
│  └────────┬─────────┘   └──────────┬──────────┘  │
│           │                        │             │
│           ▼                        ▼             │
│  ┌─────────────────────────────────────────────┐ │
│  │           EventDebouncer                     │ │
│  │  防抖 (2s) + 批量合并 + 去重                  │ │
│  └──────────────────────┬──────────────────────┘ │
│                         │                        │
│                         ▼                        │
│  ┌─────────────────────────────────────────────┐ │
│  │      Spring ApplicationEvent 发布            │ │
│  │  FileCreatedEvent / FileModifiedEvent /      │ │
│  │  FileDeletedEvent / FileBatchScanEvent       │ │
│  └─────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

## 3. 核心组件

### 3.1 DirectoryWatcher

```java
@Service
public class DirectoryWatcherService {
    private final Map<Path, WatchKey> watchedPaths = new ConcurrentHashMap<>();
    private WatchService watchService;

    /** 启动时注册所有媒体库路径 */
    @PostConstruct
    public void init() {
        watchService = FileSystems.getDefault().newWatchService();
        libraryPathRepository.findAll().forEach(lp -> registerPath(Path.of(lp.getPath())));
        // 启动虚拟线程轮询事件
        Thread.startVirtualThread(this::pollEvents);
    }

    /** 递归注册目录及所有子目录 */
    void registerPath(Path dir) {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                WatchKey key = d.register(watchService,
                    ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                watchedPaths.put(d, key);
                return CONTINUE;
            }
        });
    }

    /** 动态添加/移除监听路径 (媒体库配置变更时) */
    void addLibraryPath(LibraryPath lp) { registerPath(Path.of(lp.getPath())); }
    void removeLibraryPath(LibraryPath lp) { unregisterPath(Path.of(lp.getPath())); }
}
```

### 3.2 防抖与批量合并

```java
@Component
public class EventDebouncer {
    // 缓冲窗口: 2秒内同一文件的多次事件合并为一次
    private final Map<Path, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public void debounce(Path path, WatchEvent.Kind<?> kind) {
        ScheduledFuture<?> existing = pending.remove(path);
        if (existing != null) existing.cancel(false);

        pending.put(path, scheduler.schedule(() -> {
            pending.remove(path);
            publishEvent(path, kind);
        }, 2, TimeUnit.SECONDS));
    }
}
```

### 3.3 文件过滤

仅处理支持的媒体文件格式：

| 类型 | 扩展名 |
|------|--------|
| 视频 | `.mp4` `.mkv` `.avi` `.mov` `.wmv` `.flv` `.ts` `.m4v` `.webm` |
| 图片 | `.jpg` `.jpeg` `.png` `.gif` `.bmp` `.webp` `.tiff` `.raw` `.heic` |
| 音频 | `.mp3` `.flac` `.wav` `.aac` `.ogg` `.wma` `.m4a` `.opus` |
| 元数据 | `.nfo` `.xml` (触发元数据重载) |
| 忽略 | `.txt` `.srt` `.sub` `.idx` `.log` 等 |

## 4. 扫描策略

### 4.1 启动时全量扫描

应用启动后对所有 `auto_scan=true` 的媒体库执行全量扫描：
1. 遍历所有 LibraryPath 下的文件
2. 与数据库已有记录对比 (基于 file_path + file_modified_at)
3. 新文件 → 创建记录 + 触发元数据提取
4. 已修改 → 更新记录 + 重新提取
5. 已删除 → 标记软删除

### 4.2 定时增量扫描

```java
@Scheduled(fixedDelayString = "${mediamanager.scan.interval:1800000}") // 默认30分钟
public void scheduledScan() {
    // 仅扫描 auto_scan=true 的媒体库
    // 以媒体库各自配置的 scan_interval_minutes 为准
}
```

### 4.3 手动触发扫描

```
POST /api/v1/libraries/{id}/scan
```

## 5. 事件处理流

```
FileCreatedEvent
  │
  ├── 是否为媒体文件? → 否 → 忽略
  │                    ↓ 是
  ├── 创建 MediaItem + MediaFile 记录 (状态: UNIDENTIFIED)
  ├── 触发 MetadataPipeline
  └── Pipeline 完成 → MetadataExtractedEvent → ClassifierEngine

FileModifiedEvent
  │
  ├── 更新 MediaFile 记录 (file_size, file_modified_at)
  ├── 重新计算 checksum
  └── 重新触发 MetadataPipeline (仅技术元数据)

FileDeletedEvent
  │
  ├── 软删除 MediaFile (deleted=true, deleted_at=now)
  ├── 如果 MediaItem 下无活跃文件 → 标记 MediaItem 状态
  └── 30天后定时任务清理软删除记录
```

## 6. 异常处理

| 场景 | 处理 |
|------|------|
| 监听目录不存在/不可访问 | 日志告警，不阻塞其他目录 |
| WatchService overflow | 触发该目录全量重扫 |
| 权限不足 | 标记 LibraryPath 状态异常并通知前端 |
| 网络挂载目录断连 | 定时心跳检测，自动重连/重注册 |
