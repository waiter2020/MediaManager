INSERT OR IGNORE INTO sys_config (config_key, config_value, description) VALUES
    ('ai.default_provider', 'ollama', '默认 AI Provider：ollama / openai-compatible / noop'),
    ('ai.ollama.base_url', 'http://localhost:11434', 'Ollama 服务地址'),
    ('ai.llm_model', 'qwen2.5:7b', 'LLM 模型名称'),
    ('ai.embed_model', 'nomic-embed-text', 'Embedding 模型名称'),
    ('ai.classifier.enabled', 'true', '是否启用 AI 自动打标建议'),
    ('ai.openai.base_url', 'https://api.openai.com/v1', 'OpenAI 兼容 API Base URL'),
    ('ai.openai.api_key', '', 'OpenAI 兼容 API Key（敏感）'),
    ('ai.outbound_allowed', 'true', '是否允许出站云端 Provider');
