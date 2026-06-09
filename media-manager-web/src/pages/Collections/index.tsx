import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Tag,
  Tooltip,
  message,
} from 'antd';
import {
  AppstoreAddOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
  LockOutlined,
  PlusOutlined,
  SearchOutlined,
  ShareAltOutlined,
  SortAscendingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import EmptyState from '@/components/EmptyState';
import {
  createCollection,
  deleteCollection,
  listCollections,
  type CollectionPayload,
  type MediaCollection,
} from '@/services/collection';
import { getFileStreamUrl, resolveItemPosterUrl } from '@/services/stream';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { playVideoPreviewFromRandomPosition } from '@/utils/videoPreview';
import './List.css';

const TYPE_LABELS: Record<string, string> = {
  COLLECTION: '合集',
  PLAYLIST: '播放列表',
};

const SORT_OPTIONS = [
  { label: '名称 A-Z', value: 'name-asc' },
  { label: '名称 Z-A', value: 'name-desc' },
  { label: '最近创建', value: 'created-desc' },
  { label: '最近更新', value: 'updated-desc' },
  { label: '媒体数量', value: 'items-desc' },
];

const PREVIEWABLE_TYPES = new Set(['MOVIE', 'TV_SHOW', 'EPISODE']);

function formatRelativeTime(isoStr?: string | null): string {
  if (!isoStr) return '未知';
  const date = new Date(isoStr);
  const diff = Date.now() - date.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return '刚刚';
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return date.toLocaleDateString('zh-CN');
}

const CollectionsPage: React.FC = () => {
  const [collections, setCollections] = useState<MediaCollection[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [sortBy, setSortBy] = useState('name-asc');
  const [createOpen, setCreateOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [previewCollectionId, setPreviewCollectionId] = useState<number | null>(null);
  const [previewVideoErrors, setPreviewVideoErrors] = useState<Record<number, boolean>>({});
  const autoplayDisabled = useIsMobileAutoplayDisabled();
  const [form] = Form.useForm<CollectionPayload>();
  const smartEnabled = Form.useWatch('smart', form);

  const fetchCollections = async () => {
    setLoading(true);
    try {
      const res = await listCollections();
      if (res.code === 200) {
        setCollections(res.data || []);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCollections();
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
        history.push(`/collections/${res.data.id}`);
      }
    } finally {
      setCreateLoading(false);
    }
  };

  const handleDelete = async (e: React.MouseEvent | undefined, id: number) => {
    e?.stopPropagation();
    const res = await deleteCollection(id);
    if (res.code === 200) {
      message.success('合集已删除');
      fetchCollections();
    }
  };

  const filteredCollections = useMemo(() => {
    let list = [...collections];
    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase();
      list = list.filter(
        (collection) =>
          collection.name?.toLowerCase().includes(kw) ||
          collection.description?.toLowerCase().includes(kw),
      );
    }

    const [field, order] = sortBy.split('-');
    list.sort((a, b) => {
      if (field === 'name') {
        return order === 'asc'
          ? (a.name || '').localeCompare(b.name || '', 'zh-CN')
          : (b.name || '').localeCompare(a.name || '', 'zh-CN');
      }
      if (field === 'created') {
        const ta = new Date(a.createdAt || 0).getTime();
        const tb = new Date(b.createdAt || 0).getTime();
        return order === 'desc' ? tb - ta : ta - tb;
      }
      if (field === 'updated') {
        const ta = new Date(a.updatedAt || a.createdAt || 0).getTime();
        const tb = new Date(b.updatedAt || b.createdAt || 0).getTime();
        return order === 'desc' ? tb - ta : ta - tb;
      }
      if (field === 'items') {
        return order === 'desc'
          ? (b.itemCount || 0) - (a.itemCount || 0)
          : (a.itemCount || 0) - (b.itemCount || 0);
      }
      return 0;
    });
    return list;
  }, [collections, keyword, sortBy]);

  const totalItems = collections.reduce((sum, collection) => sum + (collection.itemCount || 0), 0);

  return (
    <div className="collections-page">
      <div className="collections-header">
        <div className="collections-header-left">
          <h2>合集</h2>
          {!loading && collections.length > 0 && (
            <div className="collections-summary">
              <span className="summary-item">
                <AppstoreAddOutlined /> {collections.length} 个合集
              </span>
              <span className="summary-divider" />
              <span className="summary-item">
                <DatabaseOutlined /> {totalItems.toLocaleString('zh-CN')} 个媒体
              </span>
            </div>
          )}
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建合集
        </Button>
      </div>

      {!loading && collections.length > 0 && (
        <div className="collections-toolbar">
          <div className="collections-toolbar-left">
            <Input
              className="collections-search"
              placeholder="搜索合集名称或描述..."
              prefix={<SearchOutlined style={{ color: 'rgba(255,255,255,0.3)' }} />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              allowClear
              style={{ width: 260 }}
            />
          </div>
          <div className="collections-toolbar-right">
            <Select
              value={sortBy}
              onChange={setSortBy}
              options={SORT_OPTIONS}
              style={{ width: 130 }}
              suffixIcon={<SortAscendingOutlined />}
              variant="borderless"
            />
          </div>
        </div>
      )}

      <div className="collections-content">
        {loading ? (
          <div className="collections-grid">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="collection-card collection-card-skeleton">
                <div className="collection-card-accent" />
                <div className="collection-card-body">
                  <div className="skeleton-row">
                    <div className="skeleton-circle" />
                    <div className="skeleton-lines">
                      <div className="skeleton-line w60" />
                      <div className="skeleton-line w40" />
                    </div>
                  </div>
                  <div className="skeleton-stats">
                    <div className="skeleton-line w30" />
                    <div className="skeleton-line w30" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : filteredCollections.length === 0 && keyword ? (
          <EmptyState description={`未找到与 "${keyword}" 相关的合集`} />
        ) : collections.length === 0 ? (
          <EmptyState
            description="还没有合集。可以在这里创建，也可以在媒体详情页把条目加入合集。"
            actionText="新建合集"
            onAction={() => setCreateOpen(true)}
          />
        ) : (
          <div className="collections-grid">
            {filteredCollections.map((collection) => {
              const url = coverUrl(collection);
              const coverItem = collection.coverItem || collection.items?.[0];
              const previewVideoUrl =
                coverItem?.fileIds?.length && PREVIEWABLE_TYPES.has(coverItem.type)
                  ? getFileStreamUrl(coverItem.fileIds[0])
                  : null;
              const showVideoPreview =
                !autoplayDisabled &&
                previewCollectionId === collection.id &&
                !!previewVideoUrl &&
                !previewVideoErrors[collection.id];
              const accentClass = collection.smart ? 'smart' : collection.visibility === 'SHARED' ? 'shared' : '';

              return (
                <div
                  key={collection.id}
                  className="collection-card"
                  onClick={() => history.push(`/collections/${collection.id}`)}
                  onMouseEnter={() => !autoplayDisabled && setPreviewCollectionId(collection.id)}
                  onMouseLeave={() =>
                    setPreviewCollectionId((current) => (current === collection.id ? null : current))
                  }
                >
                  <div className={`collection-card-accent ${accentClass}`} />
                  <div className="collection-card-body">
                    <div className="collection-card-top">
                      <div className="collection-card-cover">
                        {url ? (
                          <>
                            <img src={url} alt={collection.name} />
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
                          </>
                        ) : (
                          <AppstoreAddOutlined />
                        )}
                      </div>
                      <div className="collection-card-info">
                        <div className="collection-card-name">{collection.name}</div>
                        <div className="collection-card-tags">
                          <Tag>{TYPE_LABELS[collection.type] || collection.type}</Tag>
                          {collection.smart && (
                            <Tag icon={<ThunderboltOutlined />} color="processing">
                              Smart
                            </Tag>
                          )}
                          <Tag
                            icon={collection.visibility === 'SHARED' ? <ShareAltOutlined /> : <LockOutlined />}
                          >
                            {collection.visibility === 'SHARED' ? '共享' : '私有'}
                          </Tag>
                        </div>
                      </div>
                    </div>

                    <div className="collection-card-stats">
                      <div className="collection-card-stat">
                        <span className="stat-val">{(collection.itemCount || 0).toLocaleString('zh-CN')}</span>
                        <span className="stat-lbl">媒体</span>
                      </div>
                      <div className="collection-card-stat">
                        <span className="stat-val">
                          <ClockCircleOutlined style={{ fontSize: 12, marginRight: 3 }} />
                          {formatRelativeTime(collection.updatedAt || collection.createdAt)}
                        </span>
                        <span className="stat-lbl">最近更新</span>
                      </div>
                    </div>

                    {collection.description && (
                      <div className="collection-card-description">{collection.description}</div>
                    )}

                    <div className="collection-card-actions">
                      <Tooltip title="查看详情">
                        <Button
                          size="small"
                          type="text"
                          icon={<FolderOpenOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            history.push(`/collections/${collection.id}`);
                          }}
                        />
                      </Tooltip>
                      <div className="collection-card-actions-spacer" />
                      <Popconfirm
                        title="删除这个合集？"
                        onConfirm={(e) => handleDelete(e as React.MouseEvent | undefined, collection.id)}
                        onCancel={(e) => e?.stopPropagation()}
                      >
                        <Tooltip title="删除">
                          <Button
                            size="small"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={(e) => e.stopPropagation()}
                          />
                        </Tooltip>
                      </Popconfirm>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

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
            rule: { sortField: 'createdAt', sortOrder: 'DESC', limit: 0 },
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
              <Form.Item name={['rule', 'limit']} label="Limit" tooltip="0 表示无限制">
                <InputNumber min={0} placeholder="无限制" style={{ width: '100%' }} />
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
    </div>
  );
};

export default CollectionsPage;
