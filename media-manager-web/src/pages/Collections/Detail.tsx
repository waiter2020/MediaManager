import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  LockOutlined,
  PictureOutlined,
  ShareAltOutlined,
  SortAscendingOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import { Button, Pagination, Popconfirm, Segmented, Select, Spin, Tag, Typography, message } from 'antd';
import EmptyState from '@/components/EmptyState';
import MediaCard from '@/components/MediaCard';
import { PC_MEDIA_CARD_PREVIEW_MODE } from '@/constants/mediaPreview';
import {
  deleteCollection,
  getCollection,
  getCollectionItems,
  removeItemFromCollection,
  type CollectionRule,
  type MediaCollection,
} from '@/services/collection';
import { getFileStreamUrl, resolveItemPosterUrl } from '@/services/stream';
import { openPlayerWindow } from '@/utils/playerWindow';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { playVideoPreviewFromRandomPosition } from '@/utils/videoPreview';
import type { MediaItem } from '@/types/media';
import './Detail.css';

const TYPE_LABELS: Record<string, string> = {
  COLLECTION: '合集',
  PLAYLIST: '播放列表',
};

const BROWSE_SORT_OPTIONS = [
  { label: '最近添加', value: 'createdAt,desc' },
  { label: '名称 A-Z', value: 'title,asc' },
  { label: '名称 Z-A', value: 'title,desc' },
  { label: '评分最高', value: 'rating,desc' },
  { label: '发行日期', value: 'releaseDate,desc' },
];

const MANUAL_SORT_OPTION = { label: '手动顺序', value: 'position,asc' };

const PREVIEWABLE_TYPES = new Set(['MOVIE', 'TV_SHOW', 'EPISODE']);
const PAGE_SIZE = 30;

function ruleToSortBy(rule?: CollectionRule): string {
  const field = rule?.sortField || 'createdAt';
  const order = (rule?.sortOrder || 'DESC').toLowerCase();
  return `${field},${order}`;
}

function parseSortBy(sortBy: string): { sortField?: string; sortOrder?: 'asc' | 'desc' } {
  const [sortField, sortOrder] = sortBy.split(',');
  if (!sortField) {
    return {};
  }
  return {
    sortField,
    sortOrder: sortOrder === 'asc' ? 'asc' : 'desc',
  };
}

const CollectionDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const collectionId = Number(id);
  const access = useAccess();
  const latestFetchIdRef = useRef(0);
  const autoplayDisabled = useIsMobileAutoplayDisabled();

  const [collection, setCollection] = useState<MediaCollection | null>(null);
  const [items, setItems] = useState<MediaItem[]>([]);
  const [itemsTotal, setItemsTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [viewMode, setViewMode] = useState<string>('grid');
  const [sortBy, setSortBy] = useState('position,asc');
  const [listPreviewItemId, setListPreviewItemId] = useState<number | null>(null);
  const [listPreviewVideoErrors, setListPreviewVideoErrors] = useState<Record<number, boolean>>({});

  const sortOptions = useMemo(() => {
    if (collection?.smart) {
      return BROWSE_SORT_OPTIONS;
    }
    return [MANUAL_SORT_OPTION, ...BROWSE_SORT_OPTIONS];
  }, [collection?.smart]);

  const loadItems = useCallback(async () => {
    if (!Number.isInteger(collectionId) || collectionId <= 0) {
      return;
    }
    const fetchId = ++latestFetchIdRef.current;
    setItemsLoading(true);
    try {
      const { sortField, sortOrder } = parseSortBy(sortBy);
      const res = await getCollectionItems(collectionId, page, PAGE_SIZE, sortField, sortOrder);
      if (fetchId !== latestFetchIdRef.current) {
        return;
      }
      if (res.code === 200 && res.data) {
        setItems(res.data.items || []);
        setItemsTotal(res.data.total || 0);
      }
    } finally {
      if (fetchId === latestFetchIdRef.current) {
        setItemsLoading(false);
      }
    }
  }, [collectionId, page, sortBy]);

  useEffect(() => {
    if (!Number.isInteger(collectionId) || collectionId <= 0) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setCollection(null);
    setItems([]);
    setPage(1);

    getCollection(collectionId, false).then((res) => {
      if (cancelled) return;
      if (res.code === 200 && res.data) {
        setCollection(res.data);
        setSortBy(res.data.smart ? ruleToSortBy(res.data.rule) : 'position,asc');
      }
      setLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [collectionId]);

  useEffect(() => {
    if (!collection || loading) {
      return;
    }
    loadItems();
  }, [collection, loading, loadItems]);

  const handleDelete = async () => {
    if (!collection) return;
    await deleteCollection(collection.id);
    message.success('合集已删除');
    history.push('/collections');
  };

  const handleRemoveItem = async (item: MediaItem) => {
    if (!collection) return;
    const res = await removeItemFromCollection(collection.id, item.id);
    if (res.code === 200) {
      message.success('已移出合集');
      setCollection({ ...res.data, items: undefined });
      const nextTotal = Math.max(itemsTotal - 1, 0);
      const nextPage = Math.min(page, Math.max(1, Math.ceil(nextTotal / PAGE_SIZE)));
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await loadItems();
      }
    }
  };

  const openItemPlayer = (item: MediaItem) => {
    if (!openPlayerWindow(item.id)) {
      history.push(`/player/${item.id}`);
    }
  };

  const getListPosterUrl = (item: MediaItem): string | null =>
    resolveItemPosterUrl({
      itemId: item.id,
      posterPath: item.posterPath,
      type: item.type,
      fileIds: item.fileIds,
      thumbnailWidth: 300,
    });

  const getPlaceholderIcon = (mediaType?: string) => {
    if (mediaType === 'IMAGE') return <PictureOutlined />;
    if (mediaType === 'AUDIO') return <CustomerServiceOutlined />;
    return <VideoCameraOutlined />;
  };

  const renderItemsSkeleton = () => (
    <div className="media-grid">
      {Array.from({ length: 12 }).map((_, index) => (
        <div key={index} className="skeleton-card">
          <div className="skeleton-poster" />
          <div className="skeleton-text">
            <div className="skeleton-line" />
            <div className="skeleton-line" />
          </div>
        </div>
      ))}
    </div>
  );

  const renderGrid = () => (
    <div className="media-grid">
      {items.map((item) => (
        <div key={item.id} className="collection-media-card">
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
            previewMode={PC_MEDIA_CARD_PREVIEW_MODE}
            onClick={() => history.push(`/media/${item.id}`)}
            onPlay={
              access.canPlayMedia && ['MOVIE', 'TV_SHOW', 'AUDIO'].includes(item.type)
                ? () => openItemPlayer(item)
                : undefined
            }
          />
          {collection && !collection.smart && (
            <Button
              className="collection-remove-item"
              size="small"
              type="primary"
              danger
              icon={<DeleteOutlined />}
              aria-label={`移除 ${item.title}`}
              onClick={() => handleRemoveItem(item)}
            />
          )}
        </div>
      ))}
    </div>
  );

  const renderList = () => (
    <div className="media-list">
      {items.map((item) => {
        const listPosterUrl = getListPosterUrl(item);
        const canPreview = PREVIEWABLE_TYPES.has(item.type);
        const previewVideoUrl = item.fileIds?.length ? getFileStreamUrl(item.fileIds[0]) : null;
        const isPreviewActive = listPreviewItemId === item.id;
        const showVideoPreview =
          !autoplayDisabled && canPreview && isPreviewActive && !!previewVideoUrl && !listPreviewVideoErrors[item.id];

        return (
          <div
            key={item.id}
            className="media-list-item"
            onClick={() => history.push(`/media/${item.id}`)}
            onMouseEnter={() => !autoplayDisabled && setListPreviewItemId(item.id)}
            onMouseLeave={() => setListPreviewItemId((current) => (current === item.id ? null : current))}
          >
            <div className="media-list-poster">
              {listPosterUrl ? (
                <img className="media-list-poster-image" src={listPosterUrl} alt={item.title} />
              ) : (
                <div className="media-list-poster-placeholder">{getPlaceholderIcon(item.type)}</div>
              )}
              {showVideoPreview && (
                <video
                  className="media-list-preview-video"
                  src={previewVideoUrl || undefined}
                  muted
                  playsInline
                  preload="metadata"
                  onLoadedMetadata={(event) => {
                    playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => {
                      setListPreviewVideoErrors((prev) => ({ ...prev, [item.id]: true }));
                    });
                  }}
                  onEnded={(event) => {
                    playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => {
                      setListPreviewVideoErrors((prev) => ({ ...prev, [item.id]: true }));
                    });
                  }}
                  onError={() => {
                    setListPreviewVideoErrors((prev) => ({ ...prev, [item.id]: true }));
                  }}
                />
              )}
            </div>
            <div className="media-list-info">
              <div className="media-list-title">{item.title}</div>
              <div className="media-list-meta">
                {item.type && <Tag color="blue">{item.type}</Tag>}
                {item.libraryName && <Tag>{item.libraryName}</Tag>}
                {item.releaseDate && <span className="media-list-date">{item.releaseDate}</span>}
                {item.rating != null && item.rating > 0 && (
                  <span className="media-list-rating">★ {item.rating.toFixed(1)}</span>
                )}
              </div>
              {item.overview && (
                <div className="media-list-overview">
                  {item.overview.length > 120 ? `${item.overview.slice(0, 120)}...` : item.overview}
                </div>
              )}
            </div>
            {collection && !collection.smart && (
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label={`移除 ${item.title}`}
                onClick={(event) => {
                  event.stopPropagation();
                  handleRemoveItem(item);
                }}
              />
            )}
          </div>
        );
      })}
    </div>
  );

  if (!Number.isInteger(collectionId) || collectionId <= 0) {
    return <EmptyState description="无效的合集 ID" onAction={() => history.push('/collections')} actionText="返回合集" />;
  }

  if (loading) {
    return (
      <div className="collection-detail-loading">
        <Spin size="large" />
      </div>
    );
  }

  if (!collection) {
    return <EmptyState description="合集不存在或无权访问" onAction={() => history.push('/collections')} actionText="返回合集" />;
  }

  return (
    <div className="collection-detail-page">
      <div className="collection-detail-header">
        <div className="collection-detail-header-main">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            className="collection-detail-back"
            onClick={() => history.push('/collections')}
          >
            返回合集
          </Button>
          <div className="collection-detail-title-row">
            <h2>{collection.name}</h2>
            <Tag icon={collection.visibility === 'SHARED' ? <ShareAltOutlined /> : <LockOutlined />}>
              {collection.visibility === 'SHARED' ? '共享' : '私有'}
            </Tag>
            {collection.smart && (
              <Tag icon={<ThunderboltOutlined />} color="processing">
                Smart
              </Tag>
            )}
            <Tag icon={<UnorderedListOutlined />}>{TYPE_LABELS[collection.type] || collection.type}</Tag>
          </div>
          {collection.description && (
            <Typography.Paragraph className="collection-detail-description">
              {collection.description}
            </Typography.Paragraph>
          )}
        </div>
        <Popconfirm title="删除这个合集？" onConfirm={handleDelete}>
          <Button danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      </div>

      <div className="collection-detail-toolbar">
        <div className="collection-detail-toolbar-left">
          <span>共 {itemsTotal} 项</span>
        </div>
        <div className="collection-detail-toolbar-right">
          <Select
            value={sortBy}
            onChange={(value) => {
              setSortBy(value);
              setPage(1);
            }}
            options={sortOptions}
            style={{ width: 130 }}
            suffixIcon={<SortAscendingOutlined />}
            variant="borderless"
          />
          <Segmented
            options={[
              { value: 'grid', icon: <AppstoreOutlined /> },
              { value: 'list', icon: <UnorderedListOutlined /> },
            ]}
            value={viewMode}
            onChange={(value) => setViewMode(value as string)}
          />
        </div>
      </div>

      <div className="collection-detail-content">
        {itemsLoading ? (
          renderItemsSkeleton()
        ) : items.length > 0 ? (
          viewMode === 'list' ? renderList() : renderGrid()
        ) : (
          <EmptyState description="这个合集还没有内容。打开媒体详情页可以把条目加入合集。" />
        )}
      </div>

      {!itemsLoading && itemsTotal > 0 && (
        <div className="collection-detail-pagination">
          <span>共 {itemsTotal} 项</span>
          <Pagination
            current={page}
            total={itemsTotal}
            pageSize={PAGE_SIZE}
            onChange={setPage}
            showSizeChanger={false}
            showQuickJumper={itemsTotal > PAGE_SIZE * 5}
          />
        </div>
      )}
    </div>
  );
};

export default CollectionDetailPage;
