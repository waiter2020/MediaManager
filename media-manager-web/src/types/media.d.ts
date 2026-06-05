export type MediaType = 'MOVIE' | 'TV_SHOW' | 'EPISODE' | 'IMAGE' | 'AUDIO' | 'MIXED';

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

export interface MediaSubtitle {
  id: number;
  mediaItemId?: number;
  mediaFileId?: number;
  fileName?: string;
  language?: string;
  format?: string;
  title?: string;
  source?: string;
  provider?: string;
  externalId?: string;
  fileSize?: number;
  defaultTrack?: boolean;
  forced?: boolean;
}

export interface MediaChapter {
  id: number;
  mediaFileId: number;
  chapterIndex: number;
  title?: string;
  startSeconds: number;
  endSeconds?: number;
  source?: 'EMBEDDED' | 'GENERATED' | string;
  thumbnailAvailable?: boolean;
}

export interface MediaItem {
  id: number;
  libraryId: number;
  libraryName?: string;
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
  fileIds?: number[];
  chapters?: MediaChapter[];
  subtitles?: MediaSubtitle[];
  tags?: { id: number; name: string; color?: string }[];
  categories?: { id: number; name: string }[];
  movieMetadata?: {
    tagline?: string;
    runtimeMinutes?: number;
    certification?: string;
    genres?: string[];
    studios?: string[];
  };
  imageMetadata?: {
    width?: number;
    height?: number;
    cameraMake?: string;
    cameraModel?: string;
    lens?: string;
    iso?: string;
    aperture?: string;
    shutterSpeed?: string;
    takenAt?: string;
    gpsLatitude?: number;
    gpsLongitude?: number;
  };
  audioMetadata?: {
    artist?: string;
    album?: string;
    albumArtist?: string;
    trackNumber?: number;
    discNumber?: number;
    genres?: string[];
    durationSeconds?: number;
    bitrate?: number;
    sampleRate?: number;
    channels?: number;
  };
  tvShowMetadata?: {
    status?: string;
    network?: string;
    genres?: string[] | string;
  };
  playbackPosition?: number;
  playbackDuration?: number;
  playbackPercent?: number;
  watched?: boolean;
  favorited?: boolean;
  watchlisted?: boolean;
}
