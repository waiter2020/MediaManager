INSERT OR IGNORE INTO sys_config (config_key, config_value, description)
SELECT
    'ai.llm_provider',
    COALESCE((SELECT NULLIF(config_value, '') FROM sys_config WHERE config_key = 'ai.default_provider'), 'ollama'),
    'LLM Provider：ollama / openai-compatible / noop';

INSERT OR IGNORE INTO sys_config (config_key, config_value, description)
SELECT
    'ai.embed_provider',
    COALESCE((SELECT NULLIF(config_value, '') FROM sys_config WHERE config_key = 'ai.default_provider'), 'ollama'),
    'Embedding Provider：ollama / openai-compatible / noop';
