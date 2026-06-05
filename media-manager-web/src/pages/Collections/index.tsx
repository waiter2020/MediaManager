import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, Modal, Pagination, Popconfirm, Select, Spin, Switch, Tag, Typography, message } from 'antd';
import {
  AppstoreAddOutlined,
  DeleteOutlined,
  LockOutlined,
  PlusOutlined,
  ShareAltOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import EmptyState from '@/components/EmptyState';
import MediaCard from '@/components/MediaCard';
import {
  createCollection,
  deleteCollection,
  getCollection,
  getCollectionItems,
  listCollections,
  removeItemFromCollection,
  type CollectionPayload,
  type MediaCollection,
} from '@/services/collection';
import { getFileStreamUrl, resolveItemPosterUrl } from '@/services/stream';
import { openPlayerWindow } from '@/utils/playerWindow';
import { playVideoPreviewFromRandomPosition } from '@/utils/videoPreview';
import type { MediaItem } from '@/types/media';
import './index.css';

const typeLabels: Record<string, string> = {
  COLLECTION: '合集',
  PLAYLIST: '播放列表',
};

const previewableTypes = new Set(['MOVIE', 'TV_SHOW', 'EPISODE']);
const collectionPageSize = 30;

const CollectionsPage: React.FC = () => {
  const access = useAccess();
  const [collections, setCollections] = useState<MediaCollection[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [active, setActive] = useState<MediaCollection | null>(null);
  const [items, setItems] = useState<MediaItem[]>([]);
  const [itemsTotal, setItemsTotal] = useState(0);
  const [itemsPage, setItemsPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [previewCollectionId, setPreviewCollectionId] = useState<number | null>(null);
  const [previewVideoErrors, setPreviewVideoErrors] = useState<Record<number, boolean>>({});
  const [form] = Form.useForm<CollectionPayload>();
  const smartEnabled = Form.useWatch('smart', form);

  const loadCollections = async (nextActiveId?: number) => {
    setLoading(true);
    try {
      const res = await listCollections();
      if (res.code === 200) {
        const rows = res.data || [];
        setCollections(rows);
        const resolvedId = nextActiveId ?? activeId ?? rows[0]?.id ?? null;
        setActiveId(resolvedId);
        if (resolvedId) {
          await loadDetail(resolvedId);
        } else {
          setActive(null);
        }
      }
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (id: number) => {
    setActiveId(id);
    setItems([]);
    setItemsTotal(0);
    setItemsPage(1);
    setDetailLoading(true);
    setItemsLoading(true);
    try {
      const [detailRes, itemsRes] = await Promise.all([
        getCollection(id, false),
        getCollectionItems(id, 1, collectionPageSize),
      ]);
      if (detailRes.code === 200) {
        setActive(detailRes.data);
      }
      if (itemsRes.code === 200 && itemsRes.data) {
        setItems(itemsRes.data.items || []);
        setItemsTotal(itemsRes.data.total || 0);
      }
    } finally {
      setDetailLoading(false);
      setItemsLoading(false);
    }
  };

  const loadItemsPage = async (id: number, nextPage: number) => {
    setItemsLoading(true);
    try {
      const res = await getCollectionItems(id, nextPage, collectionPageSize);
      if (res.code === 200 && res.data) {
        setItems(res.data.items || []);
        setItemsTotal(res.data.total || 0);
        setItemsPage(nextPage);
      }
    } finally {
      setItemsLoading(false);
    }
  };

  useEffect(() => {
    loadCollections();
  }, []);

  const coverUrl = (collection: MediaCollection) => {
    const item = collection.coverItem || collection.items?.[0];
    if (!item) return null;
    return resolveItemPosterUrl({
      itemId: item.id,
      posterPath: item.posterPath,
      type: item.type,
      fileIds: item.fileIds,
      thumbnailWidth: 360,
    });
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    setCreateLoading(true);
    try {
      const res = await createCollection({
        ...values,
        type: values.type || 'COLLECTION',
        visibility: values.visibility || 'PRIVATE',
        smart: !!values.smart,
        rule: values.smart ? values.rule : undefined,
      });
      if (res.code === 200 && res.data) {
        message.success('合集已创建');
        setCreateOpen(false);
        form.resetFields();
        await loadCollections(res.data.id);
      }
    } finally {
      setCreateLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!active) return;
    await deleteCollection(active.id);
    message.success('合集已删除');
    const next = collections.find((item) => item.id !== active.id);
    await loadCollections(next?.id);
  };

  const handleRemoveItem = async (item: MediaItem) => {
    if (!active) return;
    const res = await removeItemFromCollection(active.id, item.id);
    if (res.code === 200) {
      message.success('已移出合集');
      setActive({ ...res.data, items: undefined });
      setCollections((prev) =>
        prev.map((collection) =>
          collection.id === active.id ? { ...collection, itemCount: res.data.itemCount, coverItem: res.data.coverItem } : collection,
        ),
      );
      const nextTotal = Math.max(itemsTotal - 1, 0);
      const nextPage = Math.min(itemsPage, Math.max(1, Math.ceil(nextTotal / collectionPageSize)));
      await loadItemsPage(active.id, nextPage);
    }
  };

  const openItemPlayer = (item: MediaItem) => {
    if (!openPlayerWindow(item.id)) {
      history.push(`/player/${item.id}`);
    }
  };

  const renderItemsSkeleton = () => (
    <div className="media-grid collection-media-grid">
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

  const renderCollectionTile = (collection: MediaCollection) => {
    const url = coverUrl(collection);
    const coverItem = collection.coverItem || collection.items?.[0];
    const previewVideoUrl =
      coverItem?.fileIds?.length && previewableTypes.has(coverItem.type)
        ? getFileStreamUrl(coverItem.fileIds[0])
        : null;
    const showVideoPreview =
      previewCollectionId === collection.id && !!previewVideoUrl && !previewVideoErrors[collection.id];
    const selected = collection.id === activeId;
    return (
      <button
        key={collection.id}
        type="button"
        className={`collection-tile${selected ? ' selected' : ''}`}
        onClick={() => loadDetail(collection.id)}
        onMouseEnter={() => setPreviewCollectionId(collection.id)}
        onMouseLeave={() =>
          setPreviewCollectionId((current) => (current === collection.id ? null : current))
        }
      >
        <div className="collection-cover">
          {url ? <img src={url} alt={collection.name} /> : <AppstoreAddOutlined />}
          {showVideoPreview && (
            <video
              src={previewVideoUrl || undefined}
              muted
              playsInline
              preload="metadata"
              onLoadedMetadata={(event) => {
                playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => {
                  setPreviewVideoErrors((prev) => ({ ...prev, [collection.id]: true }));
                });
              }}
              onEnded={(event) => {
                playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => {
                  setPreviewVideoErrors((prev) => ({ ...prev, [collection.id]: true }));
                });
              }}
              onError={() => {
                setPreviewVideoErrors((prev) => ({ ...prev, [collection.id]: true }));
              }}
            />
          )}
        </div>
        <div className="collection-tile-body">
          <div className="collection-tile-title" title={collection.name}>
            {collection.name}
          </div>
          <div className="collection-tile-meta">
            <span>{collection.itemCount || 0} 项</span>
            {collection.smart && <span>Smart</span>}
            <span>{typeLabels[collection.type] || collection.type}</span>
          </div>
        </div>
      </button>
    );
  };

  return (
    <PageContainer
      title="合集"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建
        </Button>
      }
    >
      {loading ? (
        <div className="collections-loading">
          <Spin size="large" />
        </div>
      ) : collections.length === 0 ? (
        <EmptyState
          description="还没有合集。可以在这里创建，也可以在媒体详情页把条目加入合集。"
          actionText="新建合集"
          onAction={() => setCreateOpen(true)}
        />
      ) : (
        <div className="collections-layout">
          <aside className="collections-sidebar">{collections.map(renderCollectionTile)}</aside>
          <section className="collection-detail">
            {detailLoading || !active ? (
              <div className="collections-loading">
                <Spin />
              </div>
            ) : (
              <>
                <div className="collection-heading">
                  <div>
                    <div className="collection-title-row">
                      <h2>{active.name}</h2>
                      <Tag icon={active.visibility === 'SHARED' ? <ShareAltOutlined /> : <LockOutlined />}>
                        {active.visibility === 'SHARED' ? '共享' : '私有'}
                      </Tag>
                      {active.smart && <Tag icon={<ThunderboltOutlined />} color="processing">Smart</Tag>}
                      <Tag icon={<UnorderedListOutlined />}>{typeLabels[active.type] || active.type}</Tag>
                    </div>
                    {active.description && (
                      <Typography.Paragraph className="collection-description">
                        {active.description}
                      </Typography.Paragraph>
                    )}
                  </div>
                  <Popconfirm title="删除这个合集？" onConfirm={handleDelete}>
                    <Button danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>
                </div>

                <div className="collection-content-heading">
                  <h3>合集内容</h3>
                  <span>共 {itemsTotal} 项</span>
                </div>

                <div className="collection-content">
                  {itemsLoading ? (
                    renderItemsSkeleton()
                  ) : items.length > 0 ? (
                    <div className="media-grid collection-media-grid">
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
                            previewMode="hover"
                            onClick={() => history.push(`/media/${item.id}`)}
                            onPlay={
                              access.canPlayMedia && ['MOVIE', 'TV_SHOW', 'AUDIO'].includes(item.type)
                                ? () => openItemPlayer(item)
                                : undefined
                            }
                          />
                          {!active.smart && (
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
                  ) : (
                    <EmptyState description="这个合集还没有内容。打开媒体详情页可以把条目加入合集。" />
                  )}
                </div>

                {!itemsLoading && itemsTotal > 0 && (
                  <div className="collection-pagination">
                    <span>共 {itemsTotal} 项</span>
                    <Pagination
                      current={itemsPage}
                      total={itemsTotal}
                      pageSize={collectionPageSize}
                      onChange={(nextPage) => active && loadItemsPage(active.id, nextPage)}
                      showSizeChanger={false}
                      showQuickJumper={itemsTotal > collectionPageSize * 5}
                    />
                  </div>
                )}
              </>
            )}
          </section>
        </div>
      )}

      <Modal
        title="新建合集"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        confirmLoading={createLoading}
        okText="创建"
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            type: 'COLLECTION',
            visibility: 'PRIVATE',
            smart: false,
            rule: { sortField: 'createdAt', sortOrder: 'DESC', limit: 50 },
          }}
          style={{ marginTop: 16 }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Select
              options={[
                { label: '合集', value: 'COLLECTION' },
                { label: '播放列表', value: 'PLAYLIST' },
              ]}
            />
          </Form.Item>
          <Form.Item name="visibility" label="可见性">
            <Select
              options={[
                { label: '私有', value: 'PRIVATE' },
                { label: '共享', value: 'SHARED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="smart" label="Smart" valuePropName="checked">
            <Switch />
          </Form.Item>
          {smartEnabled && (
            <div className="collection-rule-grid">
              <Form.Item name={['rule', 'type']} label="Media type">
                <Select
                  allowClear
                  options={[
                    { label: 'Movie', value: 'MOVIE' },
                    { label: 'TV show', value: 'TV_SHOW' },
                    { label: 'Audio', value: 'AUDIO' },
                    { label: 'Image', value: 'IMAGE' },
                  ]}
                />
              </Form.Item>
              <Form.Item name={['rule', 'keyword']} label="Keyword">
                <Input />
              </Form.Item>
              <Form.Item name={['rule', 'minYear']} label="Min year">
                <InputNumber min={1900} max={2100} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name={['rule', 'maxYear']} label="Max year">
                <InputNumber min={1900} max={2100} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name={['rule', 'minRating']} label="Min rating">
                <InputNumber min={0} max={10} step={0.1} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name={['rule', 'limit']} label="Limit">
                <InputNumber min={1} max={200} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name={['rule', 'sortField']} label="Sort by">
                <Select
                  options={[
                    { label: 'Recently added', value: 'createdAt' },
                    { label: 'Recently updated', value: 'updatedAt' },
                    { label: 'Rating', value: 'rating' },
                    { label: 'Release date', value: 'releaseDate' },
                    { label: 'Title', value: 'title' },
                  ]}
                />
              </Form.Item>
              <Form.Item name={['rule', 'sortOrder']} label="Order">
                <Select
                  options={[
                    { label: 'Descending', value: 'DESC' },
                    { label: 'Ascending', value: 'ASC' },
                  ]}
                />
              </Form.Item>
              <Form.Item name={['rule', 'unwatchedOnly']} label="Unwatched only" valuePropName="checked">
                <Switch />
              </Form.Item>
            </div>
          )}
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default CollectionsPage;
