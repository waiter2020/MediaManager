import React, { useCallback, useEffect, useRef, useState } from 'react';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { history } from '@umijs/max';
import MediaCard, { type MediaCardPreviewMode } from '@/components/MediaCard';
import { openPlayerWindow } from '@/utils/playerWindow';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import type { MediaItem } from '@/types/media';
import './index.css';

interface HorizontalMediaRowProps {
  title: string;
  items: MediaItem[];
  viewAllLink?: string;
  loading?: boolean;
  playMode?: 'detail' | 'resume';
  autoCarousel?: boolean;
  thumbnailPreviewMode?: MediaCardPreviewMode;
}

const HorizontalMediaRow: React.FC<HorizontalMediaRowProps> = ({
  title,
  items,
  viewAllLink,
  loading = false,
  playMode = 'detail',
  autoCarousel = false,
  thumbnailPreviewMode = 'hover',
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const [isPointerInside, setIsPointerInside] = useState(false);
  const autoplayDisabled = useIsMobileAutoplayDisabled();
  const effectiveThumbnailPreviewMode = autoplayDisabled ? 'none' : thumbnailPreviewMode;
  const shouldAutoCarousel = autoCarousel && !autoplayDisabled;

  const openItemPlayer = (item: MediaItem) => {
    const position = item.playbackPosition && item.playbackPosition > 30 ? item.playbackPosition : 0;
    if (!openPlayerWindow(item.id, { position })) {
      history.push(`/player/${item.id}${position > 0 ? `?t=${position}` : ''}`);
    }
  };

  const navigateItem = (item: MediaItem) => {
    const playable = item.type && ['MOVIE', 'TV_SHOW', 'AUDIO'].includes(item.type);
    if (playMode === 'resume' && playable) {
      openItemPlayer(item);
      return;
    }
    history.push(`/media/${item.id}`);
  };

  const checkScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 10);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 10);
  }, []);

  useEffect(() => {
    checkScroll();
    const el = scrollRef.current;
    if (!el) return undefined;
    el.addEventListener('scroll', checkScroll, { passive: true });
    window.addEventListener('resize', checkScroll);
    return () => {
      el.removeEventListener('scroll', checkScroll);
      window.removeEventListener('resize', checkScroll);
    };
  }, [checkScroll, items]);

  useEffect(() => {
    if (!shouldAutoCarousel || loading || items.length <= 1 || isPointerInside) return undefined;

    const interval = window.setInterval(() => {
      const el = scrollRef.current;
      if (!el || el.scrollWidth <= el.clientWidth + 8) return;

      const firstItem = el.querySelector<HTMLElement>('.row-scroll-item');
      const styles = window.getComputedStyle(el);
      const gap = Number.parseFloat(styles.columnGap || styles.gap || '16') || 16;
      const step = firstItem ? firstItem.offsetWidth + gap : el.clientWidth * 0.75;
      const nearEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - step - 8;

      el.scrollTo({
        left: nearEnd ? 0 : el.scrollLeft + step,
        behavior: 'smooth',
      });
    }, 4200);

    return () => window.clearInterval(interval);
  }, [isPointerInside, items.length, loading, shouldAutoCarousel]);

  const scroll = (direction: 'left' | 'right') => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.75;
    el.scrollBy({
      left: direction === 'left' ? -scrollAmount : scrollAmount,
      behavior: 'smooth',
    });
  };

  if (loading) {
    return (
      <div className="horizontal-media-row">
        <div className="row-header">
          <h3 className="row-title">{title}</h3>
        </div>
        <div style={{ display: 'flex', gap: 16 }}>
          {Array.from({ length: 7 }).map((_, i) => (
          <div key={i} className="skeleton-card" style={{ flex: '0 0 var(--row-item-width, 170px)' }}>
              <div className="skeleton-poster" />
              <div className="skeleton-text">
                <div className="skeleton-line" />
                <div className="skeleton-line" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (!items.length) return null;

  return (
    <div className="horizontal-media-row">
      <div className="row-header">
        <h3 className="row-title">{title}</h3>
        {viewAllLink && (
          <div className="row-actions">
            <Button type="link" size="small" onClick={() => history.push(viewAllLink)}>
              查看全部
            </Button>
          </div>
        )}
      </div>
      <div
        className="row-scroll-container"
        onMouseEnter={() => setIsPointerInside(true)}
        onMouseLeave={() => setIsPointerInside(false)}
      >
        {canScrollLeft && (
          <button className="scroll-btn scroll-left" onClick={() => scroll('left')}>
            <LeftOutlined />
          </button>
        )}
        <div className="row-scroll" ref={scrollRef}>
          {items.map((item) => (
            <div key={item.id} className="row-scroll-item">
              <MediaCard
                id={item.id}
                title={item.title}
                type={item.type}
                posterPath={item.posterPath}
                fileIds={item.fileIds}
                rating={item.rating}
                releaseDate={item.releaseDate}
                overview={item.overview}
                libraryName={item.libraryName}
                tags={item.tags}
                categories={item.categories}
                playbackPercent={item.playbackPercent}
                watched={item.watched}
                favorited={item.favorited}
                watchlisted={item.watchlisted}
                previewMode={effectiveThumbnailPreviewMode}
                onClick={() => navigateItem(item)}
                onPlay={item.type && ['MOVIE', 'TV_SHOW', 'AUDIO'].includes(item.type) ? () => openItemPlayer(item) : undefined}
              />
            </div>
          ))}
        </div>
        {canScrollRight && (
          <button className="scroll-btn scroll-right" onClick={() => scroll('right')}>
            <RightOutlined />
          </button>
        )}
      </div>
    </div>
  );
};

export default HorizontalMediaRow;
