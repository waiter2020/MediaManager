INSERT INTO sys_config (config_key, config_value, description) VALUES
    ('playback.hardware_acceleration', 'auto', 'Hardware acceleration API: none, nvenc, qsv, vaapi, amf, auto'),
    ('playback.hardware_device', '/dev/dri/renderD128', 'VA-API / QSV device path on Linux (e.g. /dev/dri/renderD128)')
ON CONFLICT (config_key) DO NOTHING;

UPDATE sys_config
SET description = 'Advanced override: FFmpeg encoder name; leave empty to use acceleration template'
WHERE config_key = 'playback.hardware_encoder';
