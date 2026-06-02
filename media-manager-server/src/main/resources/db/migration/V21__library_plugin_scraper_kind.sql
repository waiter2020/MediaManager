-- TMDB / remote scrapers should be SCRAPER, not EXTRACTOR (V14 migration used EXTRACTOR for all legacy rows).
UPDATE library_plugin_config
SET kind = 'SCRAPER'
WHERE plugin_id IN ('tmdb', 'javbus', 'stashdb')
  AND kind = 'EXTRACTOR';
