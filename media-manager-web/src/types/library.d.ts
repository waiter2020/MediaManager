export type LibraryType = 'MOVIE' | 'TV_SHOW' | 'IMAGE' | 'AUDIO' | 'MIXED';
export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

export interface LibraryPath {
  id?: number;
  path: string;
  priority: number;
}

/** @deprecated Prefer LibraryPluginConfig; API mirrors EXTRACTOR rows from plugins[] */
export interface LibraryExtractorConfig {
  id?: number;
  type?: string;
  extractorType?: string;
  priority: number;
  enabled: boolean;
  config?: string | { [key: string]: JsonValue };
}

export interface LibraryPluginConfig {
  pluginId: string;
  kind: 'EXTRACTOR' | 'SCRAPER' | 'CLASSIFIER' | 'AI_PROVIDER' | string;
  enabled: boolean;
  priority: number;
  config?: string;
}

export interface LibraryScanRequest {
  refreshMetadata?: boolean;
  scanMissingMetadata?: boolean;
  reconcileMissing?: boolean;
  scrapeAfterScan?: boolean;
  scrapeTargetStatus?: 'UNIDENTIFIED' | 'IDENTIFIED' | 'ALL';
  skipPostProcess?: boolean;
}

export interface MediaLibrary {
  id: number;
  name: string;
  type: LibraryType;
  language?: string;
  autoScan?: boolean;
  scanIntervalMinutes?: number;
  createdAt?: string;
  updatedAt?: string;
  lastScannedAt?: string;
  totalItems?: number;
  paths?: LibraryPath[];
  /** @deprecated Use plugins[] */
  extractors?: LibraryExtractorConfig[];
  plugins?: LibraryPluginConfig[];
}

