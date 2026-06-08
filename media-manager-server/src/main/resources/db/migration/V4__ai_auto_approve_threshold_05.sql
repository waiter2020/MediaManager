UPDATE sys_config
SET config_value = '0.5'
WHERE config_key = 'ai.auto_approve.confidence_threshold'
  AND config_value = '0.8';
