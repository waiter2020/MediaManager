INSERT INTO sys_role (code, name, description, built_in) VALUES
    ('SUPER_ADMIN', '超级管理员', '拥有系统所有权限', 1),
    ('ADMIN', '管理员', '可管理媒体库、用户和系统设置', 1),
    ('USER', '普通用户', '可浏览媒体、播放、编辑元数据/标签', 1),
    ('GUEST', '访客', '仅可浏览和播放媒体', 1);

-- Super Admin 拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.code = 'SUPER_ADMIN';

-- Admin 权限 (除 user:manage_admin 外)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p 
WHERE r.code = 'ADMIN' AND p.code != 'user:manage_admin';

-- User 权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p 
WHERE r.code = 'USER' AND p.code IN (
    'library:scan', 'library:view', 'media:view', 'media:play', 
    'media:edit_metadata', 'media:refresh', 'tag:manage', 'tag:assign', 'task:view'
);

-- Guest 权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p 
WHERE r.code = 'GUEST' AND p.code IN ('library:view', 'media:view', 'media:play');
