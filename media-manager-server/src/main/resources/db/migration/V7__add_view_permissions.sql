-- Add missing view permissions for tags and categories
INSERT INTO sys_permission (code, name, group_name) VALUES
    ('tag:view', '查看标签', 'tag'),
    ('category:view', '查看分类', 'category');

