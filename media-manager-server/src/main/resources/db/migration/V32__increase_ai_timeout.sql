INSERT OR IGNORE INTO sys_config (config_key, config_value, description) VALUES
    ('ai.timeout_ms', '600000', 'AI request timeout (milliseconds)');

UPDATE sys_config
SET config_value = '600000',
    description = 'AI request timeout (milliseconds)'
WHERE config_key = 'ai.timeout_ms';
