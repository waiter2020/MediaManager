export function pluginKindLabel(kind: string): string {
  switch (kind?.toUpperCase()) {
    case 'EXTRACTOR':
      return '提取器';
    case 'SCRAPER':
      return '刮削器';
    case 'CLASSIFIER':
      return '分类器';
    case 'AI_PROVIDER':
      return 'AI';
    default:
      return kind || '-';
  }
}

export function hasEnabledScraper(
  plugins: { kind?: string; enabled?: boolean }[] | undefined,
): boolean {
  return plugins?.some((plugin) => plugin.kind === 'SCRAPER' && plugin.enabled !== false) ?? false;
}

export function libraryTypeNeedsScraper(libraryType: string | undefined): boolean {
  const type = (libraryType || '').toUpperCase();
  return type === 'MOVIE' || type === 'TV_SHOW' || type === 'MIXED';
}
