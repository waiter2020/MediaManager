INSERT OR IGNORE INTO sys_config (config_key, config_value, description) VALUES
    ('ai.openai.llm.base_url', '', 'OpenAI-compatible LLM API Base URL; empty falls back to ai.openai.base_url'),
    ('ai.openai.llm.api_key', '', 'OpenAI-compatible LLM API Key; empty falls back to ai.openai.api_key'),
    ('ai.openai.embed.base_url', '', 'OpenAI-compatible embedding API Base URL; empty falls back to ai.openai.base_url'),
    ('ai.openai.embed.api_key', '', 'OpenAI-compatible embedding API Key; empty falls back to ai.openai.api_key');
