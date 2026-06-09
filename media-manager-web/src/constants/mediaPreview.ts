import type { MediaCardPreviewMode } from '@/components/MediaCard';

export const PC_MEDIA_CARD_PREVIEW_MODE: MediaCardPreviewMode = 'always';

export const PC_HORIZONTAL_ROW_AUTOPLAY_PROPS = {
  autoCarousel: true,
  thumbnailPreviewMode: PC_MEDIA_CARD_PREVIEW_MODE,
} as const;
