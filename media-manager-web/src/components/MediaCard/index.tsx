import React, { useCallback, useState } from 'react';
import {
  CustomerServiceOutlined,
  FileOutlined,
  PictureOutlined,
  PlayCircleFilled,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { resolveItemPosterUrl, getItemPreviewUrl } from '@/services/stream';
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
  onClick?: () => void;
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
  onClick,
}) => {
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgError, setImgError] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const posterUrl = resolveItemPosterUrl({
    itemId: id,
    posterPath,
    type,
    fileIds,
    thumbnailWidth: 300,
  });
  const previewUrl = getItemPreviewUrl(id);
  const year = releaseDate ? releaseDate.substring(0, 4) : null;
  const showPlayIcon = type === 'MOVIE' || type === 'TV_SHOW' || type === 'AUDIO';
  const isVideo = type === 'MOVIE' || type === 'TV_SHOW';

  const handleLoad = useCallback(() => setImgLoaded(true), []);
  const handleError = useCallback(() => {
    setImgError(true);
    setImgLoaded(true);
  }, []);

  return (
    <div
      className="media-card-wrapper"
      onClick={onClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div className="media-card-poster">
        {posterUrl && !imgError ? (
          <>
            <img
              src={posterUrl}
              alt={title}
              className={imgLoaded ? 'loaded' : 'loading'}
              onLoad={handleLoad}
              onError={handleError}
              loading="lazy"
            />
            {isHovered && isVideo && (
              <img
                src={previewUrl}
                alt={`${title} preview`}
                className="media-card-preview-image"
                loading="lazy"
              />
            )}
          </>
        ) : (
          <div className="media-card-placeholder">
            <span className="placeholder-icon">{TYPE_ICONS[type || ''] || <FileOutlined />}</span>
            <span className="placeholder-text">{title}</span>
          </div>
        )}

        {showPlayIcon && (
          <div className="media-card-play">
            <PlayCircleFilled />
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
        </div>
      </div>
    </div>
  );
};

export default MediaCard;
