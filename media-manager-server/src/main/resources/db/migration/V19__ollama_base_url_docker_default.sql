-- V17 seeded localhost; inside Docker that points to the container itself, not the host Ollama.
-- Clear seed default so MEDIAMANAGER_AI_OLLAMA_BASE_URL / application.yml can apply.
UPDATE sys_config
SET config_value = ''
WHERE config_key = 'ai.ollama.base_url'
  AND TRIM(config_value) IN ('http://localhost:11434', 'http://127.0.0.1:11434');
