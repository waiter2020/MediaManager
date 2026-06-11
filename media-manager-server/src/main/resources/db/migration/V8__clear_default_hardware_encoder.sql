UPDATE sys_config
SET config_value = ''
WHERE config_key = 'playback.hardware_encoder'
  AND config_value = 'h264_nvenc';
