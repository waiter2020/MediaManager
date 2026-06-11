import type { MediaCardPreviewMode } from '@/components/MediaCard';

export const PREVIEW_HOVER_DELAY_MS = 200;

export const PREVIEW_VIEWPORT_ROOT_MARGIN = '0px 80px 0px 80px';

export const PC_MEDIA_CARD_PREVIEW_MODE: MediaCardPreviewMode = 'always';

export const PC_HORIZONTAL_ROW_AUTOPLAY_PROPS = {
  autoCarousel: true,
  thumbnailPreviewMode: PC_MEDIA_CARD_PREVIEW_MODE,
} as const;
