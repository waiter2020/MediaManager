import React, { useRef, useState, useCallback, useEffect } from 'react';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { history } from '@umijs/max';
import MediaCard from '@/components/MediaCard';
import './index.css';

interface MediaItem {
  id: number;
  title: string;
  type?: string;
  posterPath?: string | null;
  fileIds?: number[];
  rating?: number | null;
  releaseDate?: string | null;
  overview?: string | null;
}

interface HorizontalMediaRowProps {
  title: string;
  items: MediaItem[];
  viewAllLink?: string;
  loading?: boolean;
}

const HorizontalMediaRow: React.FC<HorizontalMediaRowProps> = ({
  title,
  items,
  viewAllLink,
  loading = false,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const checkScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 10);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 10);
  }, []);

  useEffect(() => {
    checkScroll();
    const el = scrollRef.current;
    if (el) {
      el.addEventListener('scroll', checkScroll, { passive: true });
      window.addEventListener('resize', checkScroll);
      return () => {
        el.removeEventListener('scroll', checkScroll);
        window.removeEventListener('resize', checkScroll);
      };
    }
  }, [checkScroll, items]);

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
            <div key={i} className="skeleton-card" style={{ flex: '0 0 170px' }}>
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
      <div className="row-scroll-container">
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
                onClick={() => history.push(`/media/${item.id}`)}
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
