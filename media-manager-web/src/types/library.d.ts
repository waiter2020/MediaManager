export type LibraryType = 'MOVIE' | 'TV_SHOW' | 'IMAGE' | 'AUDIO' | 'MIXED';

export interface LibraryPath {
  id?: number;
  path: string;
  priority: number;
}

export interface LibraryExtractorConfig {
  id?: number;
  extractorType: string;
  priority: number;
  enabled: boolean;
  config?: Record<string, any>;
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
  paths?: LibraryPath[];
  extractorConfigs?: LibraryExtractorConfig[];
}

