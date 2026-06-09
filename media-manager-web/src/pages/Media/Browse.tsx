import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Drawer,
  InputNumber,
  Modal,
  Pagination,
  Segmented,
  Select,
  Slider,
  Space,
  Tabs,
  Tag,
  message,
} from 'antd';
import {
  AppstoreOutlined,
  CloseOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  FilterOutlined,
  PictureOutlined,
  SortAscendingOutlined,
  UnorderedListOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, useAccess, useLocation } from '@umijs/max';
import { batchAddTags } from '@/services/classification';
import { classifyBatch, getFilters, getItems, type CategoryFilter } from '@/services/media';
import { getLibraries } from '@/services/library';
import { searchUnified } from '@/services/search';
import { getFileStreamUrl, resolveItemPosterUrl } from '@/services/stream';
import EmptyState from '@/components/EmptyState';
import MediaCard from '@/components/MediaCard';
import { PC_MEDIA_CARD_PREVIEW_MODE } from '@/constants/mediaPreview';
import UnifiedSearchBox from '@/components/UnifiedSearchBox';
import { openPlayerWindow } from '@/utils/playerWindow';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { playVideoPreviewFromRandomPosition } from '@/utils/videoPreview';
import type { MediaItem } from '@/types/media';
import type { MediaLibrary } from '@/types/library';
import './Browse.css';

interface TagOption {
  id: number;
  name: string;
  color?: string;
}

interface FlatCategory {
  id: number;
  name: string;
  type?: string;
}

const TYPE_TABS = [
  { key: '', label: '全部', icon: <AppstoreOutlined /> },
  { key: 'MOVIE', label: '电影', icon: <VideoCameraOutlined /> },
  { key: 'TV_SHOW', label: '剧集', icon: <VideoCameraOutlined /> },
  { key: 'EPISODE', label: '单集', icon: <VideoCameraOutlined /> },
  { key: 'IMAGE', label: '图片', icon: <PictureOutlined /> },
  { key: 'AUDIO', label: '音频', icon: <CustomerServiceOutlined /> },
];

const SORT_OPTIONS = [
  { label: '最近添加', value: 'createdAt,desc' },
  { label: '名称 A-Z', value: 'title,asc' },
  { label: '名称 Z-A', value: 'title,desc' },
  { label: '评分最高', value: 'rating,desc' },
  { label: '发行日期', value: 'releaseDate,desc' },
];

const PREVIEWABLE_TYPES = new Set(['MOVIE', 'TV_SHOW', 'EPISODE']);

function flattenCategories(nodes: CategoryFilter[] = [], result: FlatCategory[] = []): FlatCategory[] {
  nodes.forEach((node) => {
    result.push({ id: node.id, name: node.name, type: node.type });
    if (node.children?.length) {
      flattenCategories(node.children, result);
    }
  });
  return result;
}

function parseNumberListParam(params: URLSearchParams, key: string): number[] {
  const values = params
    .getAll(key)
    .flatMap((value) => value.split(','))
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isInteger(value) && value > 0);
  return Array.from(new Set(values));
}

function parsePositiveNumberParam(params: URLSearchParams, key: string): number | null {
  const rawValue = params.get(key);
  if (!rawValue) {
    return null;
  }
  const value = Number(rawValue);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function parseBrowseSearchState(search: string) {
  const params = new URLSearchParams(search);
  const query = params.get('q') ?? params.get('query') ?? '';

  return {
    libraryId: parsePositiveNumberParam(params, 'libraryId'),
    tagIds: parseNumberListParam(params, 'tagIds'),
    categoryIds: parseNumberListParam(params, 'categoryIds'),
    searchValue: query,
    keyword: query.trim(),
  };
}

const MediaBrowse: React.FC = () => {
  const access = useAccess();
  const location = useLocation();
  const browseSearchState = useMemo(() => parseBrowseSearchState(location.search), [location.search]);
  const latestFetchIdRef = useRef(0);
  const [items, setItems] = useState<MediaItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(30);
  const [viewMode, setViewMode] = useState<string>('grid');

  const [keyword, setKeyword] = useState(browseSearchState.keyword);
  const [searchValue, setSearchValue] = useState(browseSearchState.searchValue);
  const [type, setType] = useState<string>('');
  const [sortBy, setSortBy] = useState('createdAt,desc');
  const [selectedTags, setSelectedTags] = useState<number[]>(browseSearchState.tagIds);
  const [selectedCategories, setSelectedCategories] = useState<number[]>(browseSearchState.categoryIds);
  const [selectedLibraryId, setSelectedLibraryId] = useState<number | null>(browseSearchState.libraryId);
  const [minYear, setMinYear] = useState<number | undefined>();
  const [maxYear, setMaxYear] = useState<number | undefined>();
  const [minRating, setMinRating] = useState<number>(0);
  const [filterOpen, setFilterOpen] = useState(false);
  const [selectedItemIds, setSelectedItemIds] = useState<number[]>([]);
  const [batchTagOpen, setBatchTagOpen] = useState(false);
  const [batchTagIds, setBatchTagIds] = useState<number[]>([]);
  const [batchTagLoading, setBatchTagLoading] = useState(false);
  const [batchClassifyLoading, setBatchClassifyLoading] = useState(false);
  const [listPreviewItemId, setListPreviewItemId] = useState<number | null>(null);
  const [listPreviewVideoErrors, setListPreviewVideoErrors] = useState<Record<number, boolean>>({});
  const autoplayDisabled = useIsMobileAutoplayDisabled();

  const [allTags, setAllTags] = useState<TagOption[]>([]);
  const [categories, setCategories] = useState<FlatCategory[]>([]);
  const [libraries, setLibraries] = useState<MediaLibrary[]>([]);

  useEffect(() => {
    setSelectedLibraryId(browseSearchState.libraryId);
    setSelectedTags(browseSearchState.tagIds);
    setSelectedCategories(browseSearchState.categoryIds);
    setSearchValue(browseSearchState.searchValue);
    setKeyword(browseSearchState.keyword);
    setPage(1);
  }, [browseSearchState]);

  useEffect(() => {
    getFilters()
      .then((res) => {
        if (res.code === 200 && res.data) {
          setAllTags(res.data.tags || []);
          setCategories(flattenCategories(res.data.categories || []));
        }
      })
      .catch(() => {});

    getLibraries().then((res) => {
      if (res.code === 200) setLibraries(res.data || []);
    });
  }, []);

  const fetchItems = useCallback(
    async (nextPage = page, nextPageSize = pageSize) => {
      const fetchId = latestFetchIdRef.current + 1;
      latestFetchIdRef.current = fetchId;
      setLoading(true);
      try {
        const trimmedKeyword = keyword.trim();
        if (trimmedKeyword) {
          const res = await searchUnified({
            query: trimmedKeyword,
            type: type || undefined,
            libraryId: selectedLibraryId ?? undefined,
            tagIds: selectedTags.length > 0 ? selectedTags : undefined,
            categoryIds: selectedCategories.length > 0 ? selectedCategories : undefined,
            minYear,
            maxYear,
            minRating: minRating > 0 ? minRating : undefined,
            page: nextPage,
            size: nextPageSize,
          });
          if (res?.code === 200 && res?.data?.results) {
            if (fetchId !== latestFetchIdRef.current) {
              return;
            }
            setItems(res.data.results.items || []);
            setTotal(res.data.results.total || 0);
          }
          return;
        }

        const [sortField, sortOrder] = sortBy.split(',');
        const res = await getItems({
          type: type || undefined,
          libraryId: selectedLibraryId ?? undefined,
          tagIds: selectedTags.length > 0 ? selectedTags : undefined,
          categoryIds: selectedCategories.length > 0 ? selectedCategories : undefined,
          minYear,
          maxYear,
          minRating: minRating > 0 ? minRating : undefined,
          page: nextPage,
          size: nextPageSize,
          sortField,
          sortOrder: sortOrder as 'asc' | 'desc',
        });
        if (res?.code === 200 && res?.data) {
          if (fetchId !== latestFetchIdRef.current) {
            return;
          }
          setItems(res.data.items || []);
          setTotal(res.data.total || 0);
        }
      } finally {
        if (fetchId === latestFetchIdRef.current) {
          setLoading(false);
        }
      }
    },
    [
      keyword,
      type,
      sortBy,
      selectedLibraryId,
      selectedTags,
      selectedCategories,
      minYear,
      maxYear,
      minRating,
      page,
      pageSize,
    ],
  );

  useEffect(() => {
    setPage(1);
    fetchItems(1);
  }, [keyword, type, sortBy, selectedLibraryId, selectedTags, selectedCategories, minYear, maxYear, minRating]);

  useEffect(() => {
    fetchItems(page);
  }, [page]);

  const activeFilterCount =
    (selectedLibraryId != null ? 1 : 0) +
    selectedTags.length +
    selectedCategories.length +
    (minYear != null ? 1 : 0) +
    (maxYear != null ? 1 : 0) +
    (minRating > 0 ? 1 : 0);

  const tagOptions = allTags.map((tag) => ({ label: tag.name, value: tag.id }));
  const categoryOptions = categories.map((cat) => ({
    label: cat.type ? `${cat.name} (${cat.type})` : cat.name,
    value: cat.id,
  }));

  const libraryOptions = useMemo(
    () => libraries.map((lib) => ({ label: lib.name, value: lib.id })),
    [libraries],
  );

  const clearFilters = () => {
    setSelectedLibraryId(null);
    setSelectedTags([]);
    setSelectedCategories([]);
    setMinYear(undefined);
    setMaxYear(undefined);
    setMinRating(0);
  };

  const updateBrowseSearchUrl = (nextQuery: string) => {
    const params = new URLSearchParams(location.search);
    if (nextQuery.trim()) {
      params.set('q', nextQuery.trim());
    } else {
      params.delete('q');
    }
    if (selectedLibraryId != null) {
      params.set('libraryId', String(selectedLibraryId));
    } else {
      params.delete('libraryId');
    }
    if (selectedTags.length > 0) {
      params.set('tagIds', selectedTags.join(','));
    } else {
      params.delete('tagIds');
    }
    if (selectedCategories.length > 0) {
      params.set('categoryIds', selectedCategories.join(','));
    } else {
      params.delete('categoryIds');
    }
    const nextSearch = params.toString();
    history.replace(nextSearch ? `/browse?${nextSearch}` : '/browse');
  };

  const toggleSelect = (id: number) => {
    setSelectedItemIds((prev) =>
      prev.includes(id) ? prev.filter((itemId) => itemId !== id) : [...prev, id],
    );
  };

  const openItemPlayer = (item: MediaItem) => {
    if (!openPlayerWindow(item.id)) {
      history.push(`/player/${item.id}`);
    }
  };

  const handleBatchClassify = async () => {
    if (selectedItemIds.length === 0) return;
    setBatchClassifyLoading(true);
    try {
      const res = await classifyBatch(selectedItemIds);
      if (res.code === 200) {
        const data = res.data || {};
        message.success(`分类完成：成功 ${data.succeeded ?? 0}，失败 ${data.failed ?? 0}`);
        setSelectedItemIds([]);
      }
    } catch {
      message.error('批量分类失败');
    } finally {
      setBatchClassifyLoading(false);
    }
  };

  const handleBatchTag = async () => {
    if (selectedItemIds.length === 0 || batchTagIds.length === 0) return;
    setBatchTagLoading(true);
    try {
      const res = await batchAddTags(selectedItemIds, batchTagIds);
      if (res.code === 200) {
        message.success(`已为 ${selectedItemIds.length} 项添加标签`);
        setBatchTagOpen(false);
        setBatchTagIds([]);
        setSelectedItemIds([]);
        fetchItems(page);
      }
    } catch {
      message.error('批量打标失败');
    } finally {
      setBatchTagLoading(false);
    }
  };

  const renderSkeleton = () => (
    <div className="media-grid">
      {Array.from({ length: 18 }).map((_, i) => (
        <div key={i} className="skeleton-card">
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
        <div
          key={item.id}
          className={selectedItemIds.includes(item.id) ? 'browse-card-selected' : ''}
          style={{ position: 'relative' }}
        >
          {access.canEditMetadata && (
            <input
              type="checkbox"
              checked={selectedItemIds.includes(item.id)}
              onChange={() => toggleSelect(item.id)}
              onClick={(e) => e.stopPropagation()}
              style={{ position: 'absolute', top: 8, left: 8, zIndex: 2 }}
            />
          )}
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
              access.canPlayMedia && item.type && ['MOVIE', 'TV_SHOW', 'AUDIO'].includes(item.type)
                ? () => openItemPlayer(item)
                : undefined
            }
          />
        </div>
      ))}
    </div>
  );

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
          </div>
        );
      })}
    </div>
  );

  return (
    <div className="browse-page">
      <div className="browse-tabs">
        <Tabs
          activeKey={type}
          onChange={(key) => setType(key)}
          items={TYPE_TABS.map((tab) => ({
            key: tab.key,
            label: (
              <span>
                {tab.icon}
                <span style={{ marginLeft: 6 }}>{tab.label}</span>
              </span>
            ),
          }))}
          size="large"
        />
      </div>

      <div className="browse-toolbar">
        <div className="browse-toolbar-left">
          <UnifiedSearchBox
            className="browse-search"
            placeholder="搜索标题、标签、剧情，或输入自然语言"
            value={searchValue}
            onChange={setSearchValue}
            onSearch={(nextQuery) => {
              setKeyword(nextQuery);
              setPage(1);
              updateBrowseSearchUrl(nextQuery);
            }}
            onClear={() => {
              setSearchValue('');
              setKeyword('');
              setPage(1);
              updateBrowseSearchUrl('');
            }}
            size="middle"
            style={{ width: 340 }}
          />
          <Button
            icon={<FilterOutlined />}
            onClick={() => setFilterOpen(true)}
            type={activeFilterCount > 0 ? 'primary' : 'default'}
            ghost={activeFilterCount > 0}
          >
            筛选{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
          </Button>
          {activeFilterCount > 0 && (
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={clearFilters}
              style={{ color: 'rgba(255,255,255,0.45)' }}
            >
              清除
            </Button>
          )}
        </div>
        <div className="browse-toolbar-right">
          {access.canEditMetadata && selectedItemIds.length > 0 && (
            <>
              <Button loading={batchClassifyLoading} onClick={handleBatchClassify}>
                批量分类 ({selectedItemIds.length})
              </Button>
              <Button type="primary" onClick={() => setBatchTagOpen(true)}>
                批量打标 ({selectedItemIds.length})
              </Button>
            </>
          )}
          {access.canViewRecycleBin && (
            <Button icon={<DeleteOutlined />} onClick={() => history.push('/recycle-bin')}>
              回收站
            </Button>
          )}
          <Select
            value={sortBy}
            onChange={setSortBy}
            options={SORT_OPTIONS}
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
            onChange={(val) => setViewMode(val as string)}
          />
        </div>
      </div>

      {activeFilterCount > 0 && (
        <div className="browse-active-filters">
          {selectedLibraryId != null &&
            (() => {
              const lib = libraries.find((item) => item.id === selectedLibraryId);
              return lib ? (
                <Tag closable onClose={() => setSelectedLibraryId(null)}>
                  {lib.name}
                </Tag>
              ) : null;
            })()}
          {selectedTags.map((tagId) => {
            const tag = allTags.find((item) => item.id === tagId);
            return tag ? (
              <Tag
                key={`tag-${tagId}`}
                closable
                onClose={() => setSelectedTags((prev) => prev.filter((id) => id !== tagId))}
                color={tag.color || 'blue'}
              >
                {tag.name}
              </Tag>
            ) : null;
          })}
          {selectedCategories.map((categoryId) => {
            const cat = categories.find((item) => item.id === categoryId);
            return cat ? (
              <Tag
                key={`cat-${categoryId}`}
                closable
                onClose={() => setSelectedCategories((prev) => prev.filter((id) => id !== categoryId))}
              >
                {cat.name}
              </Tag>
            ) : null;
          })}
          {minYear != null && (
            <Tag closable onClose={() => setMinYear(undefined)}>
              自 {minYear} 年
            </Tag>
          )}
          {maxYear != null && (
            <Tag closable onClose={() => setMaxYear(undefined)}>
              至 {maxYear} 年
            </Tag>
          )}
          {minRating > 0 && (
            <Tag closable onClose={() => setMinRating(0)}>
              评分 ≥ {minRating.toFixed(1)}
            </Tag>
          )}
        </div>
      )}

      <div className="browse-content">
        {loading ? (
          renderSkeleton()
        ) : items.length === 0 ? (
          <EmptyState
            type={type || undefined}
            description={
              libraries.length === 0 && !keyword
                ? access.canManageLibrary
                  ? '没有可访问的媒体库。请先创建媒体库并扫描；如果是普通用户，请在用户管理中分配库权限。'
                  : '没有可访问的媒体库，请联系管理员分配查看权限。'
                : keyword
                  ? `未找到与 "${keyword}" 相关的媒体`
                  : '当前筛选条件下没有媒体，可调整筛选或扫描媒体库'
            }
            actionText={libraries.length === 0 && access.canManageLibrary ? '创建媒体库' : undefined}
            onAction={
              libraries.length === 0 && access.canManageLibrary
                ? () => history.push('/libraries/create')
                : undefined
            }
          />
        ) : viewMode === 'grid' ? (
          renderGrid()
        ) : (
          renderList()
        )}
      </div>

      {!loading && total > 0 && (
        <div className="browse-pagination">
          <span className="browse-total">共 {total} 项</span>
          <Pagination
            current={page}
            total={total}
            pageSize={pageSize}
            onChange={(nextPage) => setPage(nextPage)}
            showSizeChanger={false}
            showQuickJumper={total > pageSize * 5}
          />
        </div>
      )}

      <Drawer
        title="筛选条件"
        open={filterOpen}
        onClose={() => setFilterOpen(false)}
        width={320}
        extra={
          <Button type="link" size="small" onClick={clearFilters}>
            清除全部
          </Button>
        }
      >
        <div className="filter-section">
          <div className="filter-label">媒体库</div>
          <Select
            allowClear
            placeholder="全部媒体库"
            style={{ width: '100%' }}
            options={libraryOptions}
            value={selectedLibraryId}
            onChange={(v) => setSelectedLibraryId(v ?? null)}
          />
        </div>
        <div className="filter-section">
          <div className="filter-label">标签</div>
          <Select
            mode="multiple"
            allowClear
            placeholder="选择标签"
            style={{ width: '100%' }}
            options={tagOptions}
            value={selectedTags}
            onChange={setSelectedTags}
          />
        </div>
        <div className="filter-section">
          <div className="filter-label">分类</div>
          <Select
            mode="multiple"
            allowClear
            placeholder="选择分类"
            style={{ width: '100%' }}
            options={categoryOptions}
            value={selectedCategories}
            onChange={setSelectedCategories}
          />
        </div>
        <div className="filter-section">
          <div className="filter-label">发行年份</div>
          <Space>
            <InputNumber
              min={1900}
              max={2100}
              placeholder="起"
              value={minYear}
              onChange={(v) => setMinYear(v ?? undefined)}
            />
            <span style={{ color: 'rgba(255,255,255,0.45)' }}>至</span>
            <InputNumber
              min={1900}
              max={2100}
              placeholder="止"
              value={maxYear}
              onChange={(v) => setMaxYear(v ?? undefined)}
            />
          </Space>
        </div>
        <div className="filter-section">
          <div className="filter-label">
            最低评分{minRating > 0 ? ` ${minRating.toFixed(1)}` : ''}
          </div>
          <Slider
            min={0}
            max={10}
            step={0.5}
            value={minRating}
            onChange={setMinRating}
            marks={{ 0: '0', 5: '5', 10: '10' }}
          />
        </div>
      </Drawer>

      <Modal
        title={`批量打标：${selectedItemIds.length} 项`}
        open={batchTagOpen}
        onCancel={() => setBatchTagOpen(false)}
        onOk={handleBatchTag}
        confirmLoading={batchTagLoading}
        okText="应用"
      >
        <Select
          mode="multiple"
          placeholder="选择要添加的标签"
          style={{ width: '100%' }}
          options={tagOptions}
          value={batchTagIds}
          onChange={setBatchTagIds}
        />
      </Modal>
    </div>
  );
};

export default MediaBrowse;
