DROP TABLE IF EXISTS media_fts;

CREATE VIRTUAL TABLE media_fts USING fts5(
    item_id UNINDEXED,
    title,
    original_title,
    overview,
    file_names,
    tags,
    categories
);
