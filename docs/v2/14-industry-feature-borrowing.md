# Industry Feature Borrowing

本文记录本轮参考 Jellyfin / Plex / Emby 后落地与排期的能力。目标不是照搬品牌形态，而是把自托管媒体库用户真正需要的工作流吸收进 MediaManager。

## 参考来源

- Jellyfin: 媒体库、影片/剧集/音乐/图书/照片、Live TV & DVR、用户个性化与 SyncPlay 入口。
  https://jellyfin.org/
- Jellyfin Metadata: TMDb、OMDb、本地 NFO、插件化元数据提供方。
  https://jellyfin.org/docs/general/server/metadata/
- Jellyfin Plugins: 字幕下载、播放统计、报告、TMDb Box Sets、Trakt、DLNA 等插件方向。
  https://jellyfin.org/docs/general/server/plugins/
- Jellyfin Users: 用户级播放、删除、登录锁定、隐藏用户等策略。
  https://jellyfin.org/docs/general/server/users/
- Plex Collections: 手动合集、智能合集、按合集浏览、详情页关联合集、首页推荐行。
  https://support.plex.tv/articles/201273953-collections/
- Plex Discover: 跨来源发现、流媒体服务可用性、通用 watchlist 思路。
  https://support.plex.tv/articles/discover/
- Plex Watch Together: 多人同步播放和会话控制。
  https://support.plex.tv/articles/watch-together/
- Emby: 转码播放、Live TV、离线同步、家长控制、DLNA/投屏、元数据编辑。
  https://docs.emby.media/about.html
- Emby Parental Controls: 分级、标签限制、访问时间表。
  https://emby.media/support/articles/Parental-Controls.html

## 本轮已实现

| 能力 | 借鉴对象 | MediaManager 实现 |
| --- | --- | --- |
| 合集 / 播放列表 | Plex Collections, Jellyfin TMDb Box Sets 插件 | 新增 `media_collection` / `media_collection_item`；支持 `COLLECTION` / `PLAYLIST`、私有 / 共享、封面条目、增删条目 |
| 详情页加入合集 | Plex 详情页合集入口 | 媒体详情页新增“加入合集”，可加入已有合集或即时创建私有合集 |
| 已看 / 未看 | Jellyfin / Plex 用户活动 | 播放历史新增 `completed`、`completed_at`、`duration_seconds`、`play_count`；详情页可手动切换 |
| 继续观看增强 | Jellyfin / Plex 首页行 | 继续观看只返回未完成且有有效进度的条目；播放超过 90% 自动标记已看 |
| 收藏状态修复 | Jellyfin / Emby 用户偏好 | 后端同时返回 `favorite` / `favorited`，前端统一读取；媒体卡显示收藏状态 |
| 发现页多行推荐 | Plex Discover / Jellyfin 首页 | 新增继续观看、为你推荐、最近收藏、高分内容、还没看、最近添加 |
| 媒体卡上下文状态 | Plex/Jellyfin 卡片状态 | 卡片显示播放进度、已看、已收藏，供浏览/发现/合集复用 |

## 已有并继续保留

| 能力 | 当前状态 |
| --- | --- |
| 元数据刮削与手动识别 | 已有 TMDb / JavBus / StashDB 搜索和应用 |
| NFO | 已有导出服务，适合作为本地元数据优先链路 |
| 插件化 | 已有 extractor / scraper / classifier / AI provider 配置 |
| 相似推荐 | 已有向量相似内容接口 |
| 字幕能力 | 已有本地字幕 DTO 与在线字幕搜索入口 |
| HLS / 原画播放 | 已有播放器和进度上报 |
| 回收站 | 已有软删除与恢复入口 |
| 库级权限 | 已有 view/edit/delete_file 控制 |

## 需要独立子系统的后续能力

这些能力不能只靠一个页面补齐，已按接口方向保留：

| 能力 | 来源 | 建议形态 |
| --- | --- | --- |
| 智能合集 | Plex smart collections, Jellyfin TMDb Box Sets | 在 `media_collection` 增加规则 JSON、定时刷新任务、按标签/评分/年份/提供方 ID 自动维护 |
| 观看派对 / SyncPlay | Jellyfin SyncPlay, Plex Watch Together | WebSocket/SSE 会话、房间成员、统一播放控制、延迟校准 |
| 家长控制 | Emby Parental Controls, Jellyfin Users | 用户策略表：最大分级、标签允许/拒绝、访问时段、库/功能级限制 |
| DLNA / 投屏 | Jellyfin DLNA plugin, Emby DLNA/Chromecast | 独立 discovery / control 插件，避免污染核心媒体库 |
| Live TV / DVR | Jellyfin Live TV, Emby Live TV | M3U/XMLTV 输入、节目表、录制任务、直播转码 |
| 离线同步 | Emby Mobile Sync | 客户端下载队列、转码预设、过期清理、设备授权 |
| 播放统计报表 | Jellyfin Playback Reporting / Reports | 汇总表 + 图表页：观看时长、热门内容、用户活跃、时段热力 |
| 外部 watchlist | Plex Universal Watchlist / Discover | Provider ID 统一、外部来源状态、缺片列表与刮削任务联动 |

## Second Batch Implemented
| Capability | Borrowed from | MediaManager implementation |
| --- | --- | --- |
| Smart collections | Plex smart collections, Jellyfin box-set automation | `media_collection.smart` + `rule_json`; rule-driven results by library/type/keyword/category/tag/year/rating/sort/limit/unwatched-only. Smart collections reject manual add/remove. |
| Watchlist | Plex Universal Watchlist, Jellyfin user queues | `user_watchlist` table, `/api/v1/user/watchlist`, `/api/v1/user/watchlist/{mediaItemId}`; Discover shows a Watchlist row and detail cards expose `watchlisted`. |
| Playback stats API | Jellyfin Playback Reporting, Reports plugin | `/api/v1/user/playback/stats` returns played/completed counts, watch time, favorite/watchlist counts, recent played, most played, and watchlist samples. |
| Card/detail state | Jellyfin/Plex personalized item state | `MediaItemResponse` and detail response include `watchlisted`; media cards show a dedicated Watchlist badge and the detail page has a toggle action. |
