INSERT INTO sys_config (config_key, config_value, description) VALUES
    ('auth.enabled', 'true', '是否开启登录认证'),
    ('ffmpeg.path', 'ffmpeg', 'FFmpeg 可执行文件路径'),
    ('ffprobe.path', 'ffprobe', 'FFprobe 可执行文件路径'),
    ('tmdb.api_key', '', 'TMDb 全局 API Key (可被媒体库覆盖)'),
    ('ui.theme', 'dark', '前端默认主题');
