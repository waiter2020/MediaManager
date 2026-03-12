import React, { useState, useCallback } from 'react';
import {
  PlayCircleFilled,
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  FileOutlined,
} from '@ant-design/icons';
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
  onClick,
}) => {
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgError, setImgError] = useState(false);

  const token = localStorage.getItem('accessToken') || '';
  const tokenParam = `token=${encodeURIComponent(token)}`;
  const posterUrl = posterPath
    ? `/api/v1/items/${id}/poster?${tokenParam}`
    : type === 'IMAGE' && fileIds && fileIds.length > 0
      ? `/api/v1/stream/images/${fileIds[0]}?w=300&${tokenParam}`
      : null;
  const year = releaseDate ? releaseDate.substring(0, 4) : null;
  const showPlayIcon = type === 'MOVIE' || type === 'TV_SHOW' || type === 'AUDIO';

  const handleLoad = useCallback(() => setImgLoaded(true), []);
  const handleError = useCallback(() => {
    setImgError(true);
    setImgLoaded(true);
  }, []);

  return (
    <div className="media-card-wrapper" onClick={onClick}>
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
            <span className="placeholder-icon">
              {TYPE_ICONS[type || ''] || <FileOutlined />}
            </span>
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
            {type && (
              <span className="media-card-badge type-badge">
                {TYPE_LABELS[type] || type}
              </span>
            )}
            {rating != null && rating > 0 && (
              <span className="media-card-badge rating-badge">
                ★ {rating.toFixed(1)}
              </span>
            )}
            {year && (
              <span className="media-card-badge year-badge">{year}</span>
            )}
          </div>
          <div className="media-card-title" title={title}>
            {title}
          </div>
          {overview && (
            <div className="media-card-subtitle">
              {overview.length > 60 ? overview.slice(0, 60) + '...' : overview}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default MediaCard;
