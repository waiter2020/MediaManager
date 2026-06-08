-- Seed data for a fresh PostgreSQL database.

-- Permissions
INSERT INTO sys_permission (code, name, group_name) VALUES
    ('system:manage', '系统设置', 'system'),
    ('user:manage', '用户管理', 'user'),
    ('user:manage_admin', '管理管理员', 'user'),
    ('library:create', '创建媒体库', 'library'),
    ('library:edit', '编辑媒体库', 'library'),
    ('library:delete', '删除媒体库', 'library'),
    ('library:scan', '触发扫描', 'library'),
    ('library:view', '查看媒体库', 'library'),
    ('media:view', '浏览媒体', 'media'),
    ('media:play', '播放媒体', 'media'),
    ('media:edit_metadata', '编辑元数据', 'media'),
    ('media:delete', '删除记录', 'media'),
    ('media:delete_file', '删除源文件', 'media'),
    ('media:refresh', '刷新元数据', 'media'),
    ('tag:manage', '管理标签', 'tag'),
    ('tag:assign', '分配标签', 'tag'),
    ('category:manage', '管理分类', 'category'),
    ('task:view', '查看任务', 'task'),
    ('tag:view', '查看标签', 'tag'),
    ('category:view', '查看分类', 'category'),
    ('task:execute', '执行任务', 'task')
ON CONFLICT (code) DO NOTHING;

-- Roles
INSERT INTO sys_role (code, name, description, built_in) VALUES
    ('SUPER_ADMIN', '超级管理员', '拥有系统所有权限', TRUE),
    ('ADMIN', '管理员', '可管理媒体库、用户和系统设置', TRUE),
    ('USER', '普通用户', '可浏览媒体、播放、编辑元数据/标签', TRUE),
    ('GUEST', '访客', '仅可浏览和播放媒体', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Role permissions
-- SUPER_ADMIN owns all permissions
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- ADMIN owns all permissions except user:manage_admin
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code != 'user:manage_admin'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- USER permissions
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'library:scan', 'library:view', 'media:view', 'media:play',
    'media:edit_metadata', 'media:refresh', 'tag:manage', 'tag:assign', 'task:view'
)
WHERE r.code = 'USER'
ON CONFLICT DO NOTHING;

-- GUEST permissions
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('library:view', 'media:view', 'media:play')
WHERE r.code = 'GUEST'
ON CONFLICT DO NOTHING;

-- Base system config
INSERT INTO sys_config (config_key, config_value, description) VALUES
    ('auth.enabled', 'true', '是否开启登录认证'),
    ('ffmpeg.path', 'ffmpeg', 'FFmpeg 可执行文件路径'),
    ('ffprobe.path', 'ffprobe', 'FFprobe 可执行文件路径'),
    ('tmdb.api_key', '', 'TMDb 全局 API Key (可被媒体库覆盖)'),
    ('ui.theme', 'dark', '前端默认主题'),
    ('ai.default_provider', 'ollama', '默认 AI Provider：ollama / openai-compatible / noop'),
    ('ai.ollama.base_url', '', 'Ollama 服务地址（Docker/部署时通常通过环境变量覆盖）'),
    ('ai.llm_model', 'qwen2.5:7b', 'LLM 模型名称'),
    ('ai.embed_model', 'nomic-embed-text', 'Embedding 模型名称'),
    ('ai.classifier.enabled', 'true', '是否启用 AI 自动打标建议'),
    ('ai.openai.base_url', 'https://api.openai.com/v1', 'OpenAI 兼容 API Base URL'),
    ('ai.openai.api_key', '', 'OpenAI 兼容 API Key（敏感）'),
    ('ai.outbound_allowed', 'true', '是否允许出站云端 Provider'),
    ('ai.timeout_ms', '600000', 'AI request timeout (milliseconds)'),
    ('ai.auto_approve.enabled', 'false', '是否开启 AI 建议自动审核采纳'),
    ('ai.auto_approve.confidence_threshold', '0.5', '自动审核置信度阈值 (0.0-1.0)'),
    ('ai.auto_approve.fields', 'tag:*,overview', '允许自动审核的字段列表（逗号分隔，*表示所有）'),
    ('ai.llm_provider', 'ollama', 'LLM Provider：ollama / openai-compatible / noop'),
    ('ai.embed_provider', 'ollama', 'Embedding Provider：ollama / openai-compatible / noop'),
    ('ai.openai.llm.base_url', '', 'OpenAI-compatible LLM API Base URL; empty falls back to ai.openai.base_url'),
    ('ai.openai.llm.api_key', '', 'OpenAI-compatible LLM API Key; empty falls back to ai.openai.api_key'),
    ('ai.openai.embed.base_url', '', 'OpenAI-compatible embedding API Base URL; empty falls back to ai.openai.base_url'),
    ('ai.openai.embed.api_key', '', 'OpenAI-compatible embedding API Key; empty falls back to ai.openai.api_key'),
    ('playback.hardware_encoder', 'h264_nvenc', 'Hardware encoder used when playback transcodeMode=hardware')
ON CONFLICT (config_key) DO NOTHING;

-- Built-in categories
INSERT INTO category (id, name, type) VALUES
    (1, '电影', 'GENRE'),
    (2, '剧集', 'GENRE'),
    (3, '年份', 'YEAR'),
    (4, '分辨率', 'RESOLUTION'),
    (5, '视频编码', 'CODEC')
ON CONFLICT (id) DO NOTHING;

-- Align sequence after explicit IDs
SELECT setval(pg_get_serial_sequence('category', 'id'), (SELECT GREATEST(MAX(id), 1) FROM category), TRUE);

INSERT INTO category (name, parent_id, type) VALUES
    ('4K', 4, 'RESOLUTION'),
    ('1080p', 4, 'RESOLUTION'),
    ('720p', 4, 'RESOLUTION'),
    ('SD', 4, 'RESOLUTION')
ON CONFLICT DO NOTHING;

INSERT INTO category (name, parent_id, type) VALUES
    ('HEVC/H.265', 5, 'CODEC'),
    ('AVC/H.264', 5, 'CODEC'),
    ('AV1', 5, 'CODEC')
ON CONFLICT DO NOTHING;

