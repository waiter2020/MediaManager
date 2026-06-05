-- 内置的大类
INSERT INTO category (id, name, type) VALUES
    (1, '电影', 'GENRE'),
    (2, '剧集', 'GENRE'),
    (3, '年份', 'YEAR'),
    (4, '分辨率', 'RESOLUTION'),
    (5, '视频编码', 'CODEC');

-- 重置序列 (SQLite 不需要手动 setval)

-- 预置分辨率子类
INSERT INTO category (name, parent_id, type) VALUES
    ('4K', 4, 'RESOLUTION'),
    ('1080p', 4, 'RESOLUTION'),
    ('720p', 4, 'RESOLUTION'),
    ('SD', 4, 'RESOLUTION');

-- 预置编码子类
INSERT INTO category (name, parent_id, type) VALUES
    ('HEVC/H.265', 5, 'CODEC'),
    ('AVC/H.264', 5, 'CODEC'),
    ('AV1', 5, 'CODEC');
