export type MediaType = 'MOVIE' | 'TV_SHOW' | 'IMAGE' | 'AUDIO' | 'MIXED';

export interface MediaFile {
  id: number;
  filePath: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  container?: string;
  videoCodec?: string;
  audioCodec?: string;
  width?: number;
  height?: number;
  durationSeconds?: number;
  bitrate?: number;
  deleted?: boolean;
}

export interface MediaItem {
  id: number;
  libraryId: number;
  title: string;
  originalTitle?: string;
  sortTitle?: string;
  type: MediaType;
  status?: string;
  releaseDate?: string;
  rating?: number;
  overview?: string;
  posterPath?: string;
  backdropPath?: string;
  createdAt?: string;
  updatedAt?: string;
  files?: MediaFile[];
  tags?: { id: number; name: string; color?: string }[];
  categories?: { id: number; name: string }[];
}

