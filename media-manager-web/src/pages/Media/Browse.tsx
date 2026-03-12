import React, { useEffect, useState, useCallback } from 'react';
import { Input, Select, Space, Tabs, Pagination, Drawer, Button, Slider, Segmented, Tag } from 'antd';
import {
  AppstoreOutlined,
  UnorderedListOutlined,
  FilterOutlined,
  SortAscendingOutlined,
  SearchOutlined,
  CloseOutlined,
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { getItems } from '@/services/media';
import { getTags, getCategoryTree } from '@/services/classification';
import MediaCard from '@/components/MediaCard';
import EmptyState from '@/components/EmptyState';
import './Browse.css';

const TYPE_TABS = [
  { key: '', label: '全部', icon: <AppstoreOutlined /> },
  { key: 'MOVIE', label: '电影', icon: <VideoCameraOutlined /> },
  { key: 'TV_SHOW', label: '剧集', icon: <VideoCameraOutlined /> },
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

const MediaBrowse: React.FC = () => {
  const [items, setItems] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(30);
  const [viewMode, setViewMode] = useState<string>('grid');

  const [keyword, setKeyword] = useState('');
  const [searchValue, setSearchValue] = useState('');
  const [type, setType] = useState<string>('');
  const [sortBy, setSortBy] = useState('createdAt,desc');
  const [selectedTags, setSelectedTags] = useState<number[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<number[]>([]);
  const [filterOpen, setFilterOpen] = useState(false);

  const [allTags, setAllTags] = useState<any[]>([]);
  const [categories, setCategories] = useState<any[]>([]);

  useEffect(() => {
    getTags().then((res) => {
      if (res.code === 200) setAllTags(res.data || []);
    });
    getCategoryTree().then((res) => {
      if (res.code === 200) setCategories(flattenCategories(res.data || []));
    });
  }, []);

  const flattenCategories = (nodes: any[], result: any[] = []): any[] => {
    nodes.forEach((n) => {
      result.push({ id: n.id, name: n.name, type: n.type });
      if (n.children) flattenCategories(n.children, result);
    });
    return result;
  };

  const fetchItems = useCallback(
    async (p = page, s = pageSize) => {
      setLoading(true);
      try {
        const [sortField, sortOrder] = sortBy.split(',');
        const res = await getItems({
          keyword: keyword || undefined,
          type: type || undefined,
          tagIds: selectedTags.length > 0 ? selectedTags : undefined,
          categoryIds: selectedCategories.length > 0 ? selectedCategories : undefined,
          page: p,
          size: s,
        });
        if (res?.code === 200 && res?.data) {
          setItems(res.data.items || []);
          setTotal(res.data.total || 0);
        }
      } finally {
        setLoading(false);
      }
    },
    [keyword, type, sortBy, selectedTags, selectedCategories, page, pageSize],
  );

  useEffect(() => {
    setPage(1);
    fetchItems(1);
  }, [keyword, type, sortBy, selectedTags, selectedCategories]);

  useEffect(() => {
    fetchItems(page);
  }, [page]);

  const activeFilterCount =
    selectedTags.length + selectedCategories.length;

  const clearFilters = () => {
    setSelectedTags([]);
    setSelectedCategories([]);
  };

  const tagOptions = allTags.map((t) => ({ label: t.name, value: t.id }));
  const categoryOptions = categories.map((c) => ({
    label: `${c.name} (${c.type})`,
    value: c.id,
  }));

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
        <MediaCard
          key={item.id}
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
      ))}
    </div>
  );

  const token = localStorage.getItem('accessToken') || '';

  const getListPosterUrl = (item: any): string | null => {
    if (item.posterPath) {
      return `/api/v1/items/${item.id}/poster?token=${encodeURIComponent(token)}`;
    }
    if (item.type === 'IMAGE' && item.fileIds && item.fileIds.length > 0) {
      return `/api/v1/stream/images/${item.fileIds[0]}?w=300&token=${encodeURIComponent(token)}`;
    }
    return null;
  };

  const getPlaceholderIcon = (type?: string) => {
    if (type === 'IMAGE') return <PictureOutlined />;
    if (type === 'AUDIO') return <CustomerServiceOutlined />;
    return <VideoCameraOutlined />;
  };

  const renderList = () => (
    <div className="media-list">
      {items.map((item) => {
        const listPosterUrl = getListPosterUrl(item);
        return (
        <div
          key={item.id}
          className="media-list-item"
          onClick={() => history.push(`/media/${item.id}`)}
        >
          <div className="media-list-poster">
            {listPosterUrl ? (
              <img src={listPosterUrl} alt={item.title} />
            ) : (
              <div className="media-list-poster-placeholder">
                {getPlaceholderIcon(item.type)}
              </div>
            )}
          </div>
          <div className="media-list-info">
            <div className="media-list-title">{item.title}</div>
            <div className="media-list-meta">
              {item.type && <Tag color="blue">{item.type}</Tag>}
              {item.releaseDate && (
                <span className="media-list-date">{item.releaseDate}</span>
              )}
              {item.rating > 0 && (
                <span className="media-list-rating">★ {item.rating.toFixed(1)}</span>
              )}
            </div>
            {item.overview && (
              <div className="media-list-overview">
                {item.overview.length > 120
                  ? item.overview.slice(0, 120) + '...'
                  : item.overview}
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
      {/* Type tabs */}
      <div className="browse-tabs">
        <Tabs
          activeKey={type}
          onChange={(key) => setType(key)}
          items={TYPE_TABS.map((t) => ({
            key: t.key,
            label: (
              <span>
                {t.icon}
                <span style={{ marginLeft: 6 }}>{t.label}</span>
              </span>
            ),
          }))}
          size="large"
        />
      </div>

      {/* Toolbar */}
      <div className="browse-toolbar">
        <div className="browse-toolbar-left">
          <Input
            className="browse-search"
            placeholder="搜索标题..."
            prefix={<SearchOutlined style={{ color: 'rgba(255,255,255,0.3)' }} />}
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onPressEnter={() => setKeyword(searchValue)}
            onBlur={() => setKeyword(searchValue)}
            allowClear
            onClear={() => { setSearchValue(''); setKeyword(''); }}
            style={{ width: 240 }}
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

      {/* Active filter tags */}
      {activeFilterCount > 0 && (
        <div className="browse-active-filters">
          {selectedTags.map((tid) => {
            const tag = allTags.find((t) => t.id === tid);
            return tag ? (
              <Tag
                key={`tag-${tid}`}
                closable
                onClose={() => setSelectedTags((prev) => prev.filter((x) => x !== tid))}
                color={tag.color || 'blue'}
              >
                {tag.name}
              </Tag>
            ) : null;
          })}
          {selectedCategories.map((cid) => {
            const cat = categories.find((c) => c.id === cid);
            return cat ? (
              <Tag
                key={`cat-${cid}`}
                closable
                onClose={() =>
                  setSelectedCategories((prev) => prev.filter((x) => x !== cid))
                }
              >
                {cat.name}
              </Tag>
            ) : null;
          })}
        </div>
      )}

      {/* Content */}
      <div className="browse-content">
        {loading ? (
          renderSkeleton()
        ) : items.length === 0 ? (
          <EmptyState
            type={type || undefined}
            description={keyword ? `未找到"${keyword}"相关媒体` : undefined}
            actionText="添加媒体库"
            onAction={() => history.push('/libraries/create')}
          />
        ) : viewMode === 'grid' ? (
          renderGrid()
        ) : (
          renderList()
        )}
      </div>

      {/* Pagination */}
      {!loading && total > 0 && (
        <div className="browse-pagination">
          <span className="browse-total">共 {total} 项</span>
          <Pagination
            current={page}
            total={total}
            pageSize={pageSize}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
            showQuickJumper={total > pageSize * 5}
          />
        </div>
      )}

      {/* Filter drawer */}
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
      </Drawer>
    </div>
  );
};

export default MediaBrowse;
