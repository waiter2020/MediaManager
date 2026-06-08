import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  CheckCircleFilled,
  ClockCircleFilled,
  CustomerServiceOutlined,
  FileOutlined,
  HeartFilled,
  PictureOutlined,
  PlayCircleFilled,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { getFileStreamUrl, resolveItemPosterUrl } from '@/services/stream';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { playVideoPreviewFromRandomPosition } from '@/utils/videoPreview';
import './index.css';

const TYPE_LABELS: Record<string, string> = {
  MOVIE: '电影',
  TV_SHOW: '剧集',
  IMAGE: '图片',
  AUDIO: '音频',
};

const TYPE_ICONS: Record<string, React.ReactNode> = {
  MOVIE: <VideoCameraOutlined />,
  TV_SHOW: <VideoCameraOutlined />,
  IMAGE: <PictureOutlined />,
  AUDIO: <CustomerServiceOutlined />,
};

export type MediaCardPreviewMode = 'hover' | 'always' | 'none';

const PREVIEWABLE_TYPES = new Set(['MOVIE', 'TV_SHOW', 'EPISODE']);

interface MediaCardProps {
  id: number;
  title: string;
  type?: string;
  posterPath?: string | null;
  fileIds?: number[];
  rating?: number | null;
  releaseDate?: string | null;
  overview?: string | null;
  libraryName?: string | null;
  tags?: Array<{ id: number; name: string; color?: string | null }>;
  categories?: Array<{ id: number; name: string }>;
  playbackPercent?: number | null;
  watched?: boolean | null;
  favorited?: boolean | null;
  watchlisted?: boolean | null;
  previewMode?: MediaCardPreviewMode;
  onClick?: () => void;
  onPlay?: () => void;
}

const MediaCard: React.FC<MediaCardProps> = ({
  id,
  title,
  type,
  posterPath,
  fileIds,
  rating,
  releaseDate,
  overview,
  libraryName,
  tags = [],
  categories = [],
  playbackPercent,
  watched,
  favorited,
  watchlisted,
  previewMode = 'hover',
  onClick,
  onPlay,
}) => {
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgError, setImgError] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [previewVideoError, setPreviewVideoError] = useState(false);
  const previewVideoRef = useRef<HTMLVideoElement>(null);
  const autoplayDisabled = useIsMobileAutoplayDisabled();

  const posterUrl = resolveItemPosterUrl({
    itemId: id,
    posterPath,
    type,
    fileIds,
    thumbnailWidth: 300,
  });
  const previewVideoUrl = fileIds?.length ? getFileStreamUrl(fileIds[0]) : null;
  const year = releaseDate ? releaseDate.substring(0, 4) : null;
  const isVideo = PREVIEWABLE_TYPES.has(type || '');
  const effectivePreviewMode = autoplayDisabled ? 'none' : previewMode;
  const showPlayIcon = isVideo || type === 'AUDIO';
  const shouldShowPreview =
    isVideo && effectivePreviewMode !== 'none' && (effectivePreviewMode === 'always' || isHovered);
  const shouldShowVideoPreview = shouldShowPreview && !!previewVideoUrl && !previewVideoError;
  const progress = typeof playbackPercent === 'number' ? Math.max(0, Math.min(100, playbackPercent)) : 0;
  const showProgress = showPlayIcon && progress > 0 && progress < 95 && !watched;
  const relatedChips = [
    ...tags.map((tag) => ({
      key: `tag-${tag.id}`,
      name: tag.name,
      color: tag.color,
      type: 'tag' as const,
    })),
    ...categories.map((category) => ({
      key: `category-${category.id}`,
      name: category.name,
      type: 'category' as const,
    })),
  ].filter((chip) => chip.name);
  const visibleChips = relatedChips.slice(0, 3);
  const hiddenChipCount = Math.max(relatedChips.length - visibleChips.length, 0);

  const handleLoad = useCallback(() => setImgLoaded(true), []);
  const handleError = useCallback(() => {
    setImgError(true);
    setImgLoaded(true);
  }, []);

  useEffect(() => {
    setPreviewVideoError(false);
  }, [id, previewVideoUrl]);

  useEffect(() => {
    const video = previewVideoRef.current;
    if (!video) return;

    if (shouldShowVideoPreview) return;

    video.pause();
    if (effectivePreviewMode === 'hover') {
      try {
        video.currentTime = 0;
      } catch {
        // Some browsers disallow seeking until metadata is available.
      }
    }
  }, [effectivePreviewMode, previewVideoUrl, shouldShowVideoPreview]);

  const handlePlayClick = useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      event.stopPropagation();
      onPlay?.();
    },
    [onPlay],
  );

  return (
    <div
      className="media-card-wrapper"
      onClick={onClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div className="media-card-poster">
        {posterUrl && !imgError ? (
          <img
            src={posterUrl}
            alt={title}
            className={imgLoaded ? 'loaded' : 'loading'}
            onLoad={handleLoad}
            onError={handleError}
            loading="lazy"
          />
        ) : (
          <div className="media-card-placeholder">
            <span className="placeholder-icon">{TYPE_ICONS[type || ''] || <FileOutlined />}</span>
            <span className="placeholder-text">{title}</span>
          </div>
        )}

        {shouldShowVideoPreview && (
          <video
            ref={previewVideoRef}
            src={previewVideoUrl || undefined}
            className={`media-card-preview-video${effectivePreviewMode === 'always' ? ' is-autoplay' : ''}`}
            muted
            playsInline
            preload="metadata"
            onLoadedMetadata={(event) => {
              playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => setPreviewVideoError(true));
            }}
            onEnded={(event) => {
              playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => setPreviewVideoError(true));
            }}
            onError={() => setPreviewVideoError(true)}
          />
        )}

        {showPlayIcon && (
          onPlay ? (
            <button
              type="button"
              className="media-card-play media-card-play-button"
              aria-label={`播放 ${title}`}
              onClick={handlePlayClick}
            >
              <PlayCircleFilled />
            </button>
          ) : (
            <div className="media-card-play">
              <PlayCircleFilled />
            </div>
          )
        )}

        {(watched || favorited || watchlisted) && (
          <div className="media-card-status-row">
            {watched && (
              <span className="media-card-status watched" title="已看">
                <CheckCircleFilled />
              </span>
            )}
            {favorited && (
              <span className="media-card-status favorited" title="已收藏">
                <HeartFilled />
              </span>
            )}
            {watchlisted && (
              <span className="media-card-status watchlisted" title="Watchlist">
                <ClockCircleFilled />
              </span>
            )}
          </div>
        )}

        {showProgress && (
          <div className="media-card-progress" aria-label={`播放进度 ${progress.toFixed(0)}%`}>
            <span style={{ width: `${progress}%` }} />
          </div>
        )}

        <div className="media-card-overlay">
          <div className="media-card-meta">
            {type && <span className="media-card-badge type-badge">{TYPE_LABELS[type] || type}</span>}
            {libraryName && (
              <span className="media-card-badge library-badge" title={libraryName}>
                {libraryName}
              </span>
            )}
            {rating != null && rating > 0 && (
              <span className="media-card-badge rating-badge">★ {rating.toFixed(1)}</span>
            )}
            {year && <span className="media-card-badge year-badge">{year}</span>}
          </div>
          <div className="media-card-title" title={title}>
            {title}
          </div>
          {overview && (
            <div className="media-card-subtitle">
              {overview.length > 60 ? `${overview.slice(0, 60)}...` : overview}
            </div>
          )}
          {visibleChips.length > 0 && (
            <div className="media-card-chip-row" aria-label="媒体标签和分类">
              {visibleChips.map((chip) => (
                <span
                  key={chip.key}
                  className={`media-card-chip ${chip.type === 'category' ? 'category-chip' : 'tag-chip'}`}
                  title={chip.name}
                  style={
                    chip.type === 'tag' && chip.color
                      ? ({ '--media-card-chip-color': chip.color } as React.CSSProperties)
                      : undefined
                  }
                >
                  {chip.name}
                </span>
              ))}
              {hiddenChipCount > 0 && (
                <span className="media-card-chip more-chip">+{hiddenChipCount}</span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default MediaCard;
