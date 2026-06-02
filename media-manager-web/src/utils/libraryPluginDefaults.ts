export type PluginPreviewRow = {
  pluginId: string;
  kind: string;
  priority: number;
};

/** Mirrors backend LibraryPluginConfigService.defaultPluginsForType */
export function defaultPluginsForType(libraryType: string): PluginPreviewRow[] {
  const t = (libraryType || 'MIXED').toUpperCase();
  switch (t) {
    case 'IMAGE':
      return [
        { pluginId: 'exif', kind: 'EXTRACTOR', priority: 0 },
        { pluginId: 'ffprobe', kind: 'EXTRACTOR', priority: 10 },
      ];
    case 'AUDIO':
      return [
        { pluginId: 'ffprobe', kind: 'EXTRACTOR', priority: 0 },
        { pluginId: 'nfo', kind: 'EXTRACTOR', priority: 10 },
      ];
    case 'MOVIE':
    case 'TV_SHOW':
      return [
        { pluginId: 'nfo', kind: 'EXTRACTOR', priority: 0 },
        { pluginId: 'ffprobe', kind: 'EXTRACTOR', priority: 10 },
        { pluginId: 'tmdb', kind: 'SCRAPER', priority: 100 },
      ];
    case 'MIXED':
      return [
        { pluginId: 'nfo', kind: 'EXTRACTOR', priority: 0 },
        { pluginId: 'ffprobe', kind: 'EXTRACTOR', priority: 10 },
        { pluginId: 'exif', kind: 'EXTRACTOR', priority: 20 },
        { pluginId: 'tmdb', kind: 'SCRAPER', priority: 100 },
      ];
    default:
      return [
        { pluginId: 'nfo', kind: 'EXTRACTOR', priority: 0 },
        { pluginId: 'ffprobe', kind: 'EXTRACTOR', priority: 10 },
        { pluginId: 'tmdb', kind: 'SCRAPER', priority: 100 },
      ];
  }
}
