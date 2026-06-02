INSERT OR IGNORE INTO sys_config (config_key, config_value, description) VALUES
    ('ai.auto_approve.enabled', 'false', '是否开启 AI 建议自动审核采纳'),
    ('ai.auto_approve.confidence_threshold', '0.8', '自动审核置信度阈值 (0.0-1.0)'),
    ('ai.auto_approve.fields', 'tag:*,overview', '允许自动审核的字段列表（逗号分隔，*表示所有）');
