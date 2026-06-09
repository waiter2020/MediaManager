INSERT INTO sys_config (config_key, config_value, description) VALUES
    ('opensubtitles.api_key', '', 'OpenSubtitles API Key'),
    ('opensubtitles.username', '', 'OpenSubtitles 账号（下载字幕必填）'),
    ('opensubtitles.password', '', 'OpenSubtitles 密码（下载字幕必填）'),
    ('subtitle.default_language', 'zh-CN', '在线字幕搜索默认语言'),
    ('subtitle.user_agent', 'MediaManager/1.0', 'OpenSubtitles API User-Agent')
ON CONFLICT (config_key) DO NOTHING;
