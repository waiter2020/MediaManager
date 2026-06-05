INSERT INTO sys_config (config_key, config_value, description)
SELECT 'playback.hardware_encoder', 'h264_nvenc', 'Hardware encoder used when playback transcodeMode=hardware'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'playback.hardware_encoder'
);
