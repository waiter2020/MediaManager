-- Normalize legacy plugin rows so remote metadata sources are treated as SCRAPER plugins.
-- Earlier migrations could leave rows like plugin_id='TMDB', kind='EXTRACTOR'.
DELETE FROM library_plugin_config
WHERE id NOT IN (
    SELECT MIN(id)
    FROM library_plugin_config
    GROUP BY library_id, lower(plugin_id), upper(kind)
);

DELETE FROM library_plugin_config
WHERE id IN (
    SELECT scraper.id
    FROM library_plugin_config scraper
    WHERE upper(scraper.kind) = 'SCRAPER'
      AND lower(scraper.plugin_id) IN ('tmdb', 'javbus', 'stashdb')
      AND EXISTS (
          SELECT 1
          FROM library_plugin_config legacy
          WHERE legacy.library_id = scraper.library_id
            AND legacy.id <> scraper.id
            AND upper(legacy.kind) = 'EXTRACTOR'
            AND lower(legacy.plugin_id) = lower(scraper.plugin_id)
      )
);

UPDATE library_plugin_config
SET kind = 'SCRAPER',
    plugin_id = lower(plugin_id)
WHERE lower(plugin_id) IN ('tmdb', 'javbus', 'stashdb');

UPDATE library_plugin_config
SET kind = upper(kind),
    plugin_id = lower(plugin_id)
WHERE kind IS NOT NULL
  AND plugin_id IS NOT NULL;
