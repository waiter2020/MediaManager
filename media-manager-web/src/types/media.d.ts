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
}

