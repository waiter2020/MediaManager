import React, { useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Result,
  Spin,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { history, useAccess, useParams } from '@umijs/max';
import {
  ArrowLeftOutlined,
  AudioOutlined,
  CameraOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileOutlined,
  HddOutlined,
  HeartFilled,
  HeartOutlined,
  PictureOutlined,
  PlayCircleFilled,
  ReloadOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import HorizontalMediaRow from '@/components/HorizontalMediaRow';
import IdentifyModal from '@/components/IdentifyModal';
import TagSelect from '@/components/TagSelect';
import TvSeasonPanel, { type TvSeason } from '@/components/TvSeasonPanel';
import { addTagToItem, getTags, removeTagFromItem, type CategoryItem, type TagItem } from '@/services/classification';
import {
  classifyItem,
  deleteItem,
  deleteSourceFile,
  getItem,
  getItemDetail,
  getItemSeasons,
  getSimilarItems,
  refreshMetadata,
  syncTvSeasons,
  updateMetadata,
} from '@/services/media';
import { getRawImageUrl, resolveItemBackdropUrl, resolveItemPosterUrl } from '@/services/stream';
import { checkFavorite, toggleFavorite } from '@/services/userActivity';
import type { MediaFile, MediaItem } from '@/types/media';
import './Detail.css';

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

interface MediaDetailItem extends MediaItem {
  tvShowMetadata?: {
    status?: string;
    network?: string;
    genres?: string[] | string;
  };
}

interface MetaField {
  label: string;
  value?: React.ReactNode;
  icon?: React.ReactNode;
}

interface EditableMetadata {
  title: string;
  originalTitle?: string;
  overview?: string;
  rating?: number;
  network?: string;
  tvStatus?: string;
}

const fmtSize = (bytes?: number) => {
  if (!bytes || bytes <= 0) return '-';
  if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)} GB`;
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`;
  return `${(bytes / 1024).toFixed(1)} KB`;
};

const fmtDur = (seconds?: number) => {
  if (!seconds || seconds <= 0) return undefined;
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m} 分钟`;
};

const joinValue = (value?: string[] | string) => (Array.isArray(value) ? value.join(' / ') : value);

function buildMetadata(data: MediaDetailItem): { overview: MetaField[]; metadata: MetaField[] } {
  if (data.type === 'MOVIE') {
    const meta = data.movieMetadata;
    return {
      overview: [
        { label: '类型', value: joinValue(meta?.genres) },
        { label: '片长', value: fmtDur((meta?.runtimeMinutes || 0) * 60), icon: <ClockCircleOutlined /> },
        { label: '分级', value: meta?.certification },
        { label: '评分', value: data.rating && data.rating > 0 ? `${data.rating.toFixed(1)} / 10` : undefined },
      ],
      metadata: [
        { label: '原标题', value: data.originalTitle },
        { label: '宣传语', value: meta?.tagline },
        { label: '制片厂', value: joinValue(meta?.studios) },
        { label: '发行日期', value: data.releaseDate },
        { label: '添加时间', value: data.createdAt },
        { label: '状态', value: data.status },
      ],
    };
  }

  if (data.type === 'TV_SHOW') {
    const meta = data.tvShowMetadata;
    return {
      overview: [
        { label: '类型', value: joinValue(meta?.genres) },
        { label: '播出状态', value: meta?.status },
        { label: '电视台', value: meta?.network },
        { label: '评分', value: data.rating && data.rating > 0 ? `${data.rating.toFixed(1)} / 10` : undefined },
      ],
      metadata: [
        { label: '原标题', value: data.originalTitle },
        { label: '首播日期', value: data.releaseDate },
        { label: '添加时间', value: data.createdAt },
        { label: '识别状态', value: data.status },
      ],
    };
  }

  if (data.type === 'IMAGE') {
    const meta = data.imageMetadata;
    return {
      overview: [
        { label: '尺寸', value: meta?.width && meta?.height ? `${meta.width} x ${meta.height}` : undefined, icon: <PictureOutlined /> },
        { label: '相机', value: meta?.cameraMake ? `${meta.cameraMake} ${meta.cameraModel || ''}`.trim() : undefined, icon: <CameraOutlined /> },
        { label: '镜头', value: meta?.lens },
        { label: '拍摄时间', value: meta?.takenAt, icon: <ClockCircleOutlined /> },
      ],
      metadata: [
        { label: 'ISO', value: meta?.iso },
        { label: '光圈', value: meta?.aperture },
        { label: '快门速度', value: meta?.shutterSpeed },
        { label: 'GPS 纬度', value: meta?.gpsLatitude },
        { label: 'GPS 经度', value: meta?.gpsLongitude },
        { label: '添加时间', value: data.createdAt },
        { label: '状态', value: data.status },
      ],
    };
  }

  if (data.type === 'AUDIO') {
    const meta = data.audioMetadata;
    return {
      overview: [
        { label: '艺术家', value: meta?.artist, icon: <AudioOutlined /> },
        { label: '专辑', value: meta?.album },
        { label: '时长', value: fmtDur(meta?.durationSeconds), icon: <ClockCircleOutlined /> },
        { label: '类型', value: joinValue(meta?.genres) },
      ],
      metadata: [
        { label: '专辑艺术家', value: meta?.albumArtist },
        { label: '曲目号', value: meta?.trackNumber },
        { label: '碟号', value: meta?.discNumber },
        { label: '比特率', value: meta?.bitrate ? `${meta.bitrate} kbps` : undefined },
        { label: '采样率', value: meta?.sampleRate ? `${meta.sampleRate} Hz` : undefined },
        { label: '声道', value: meta?.channels },
        { label: '添加时间', value: data.createdAt },
        { label: '状态', value: data.status },
      ],
    };
  }

  return {
    overview: [],
    metadata: [
      { label: '原标题', value: data.originalTitle },
      { label: '发行日期', value: data.releaseDate },
      { label: '添加时间', value: data.createdAt },
      { label: '状态', value: data.status },
    ],
  };
}

const MediaDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const access = useAccess();
  const [data, setData] = useState<MediaDetailItem | null>(null);
  const [playbackPosition, setPlaybackPosition] = useState(0);
  const [loading, setLoading] = useState(true);
  const [editVisible, setEditVisible] = useState(false);
  const [editForm] = Form.useForm<EditableMetadata>();
  const [isFavorited, setIsFavorited] = useState(false);
  const [allTags, setAllTags] = useState<TagItem[]>([]);
  const [addingTag, setAddingTag] = useState(false);
  const [addTagSelectValue, setAddTagSelectValue] = useState<number | undefined>();
  const [identifyVisible, setIdentifyVisible] = useState(false);
  const [seasons, setSeasons] = useState<TvSeason[]>([]);
  const [similarItems, setSimilarItems] = useState<MediaItem[]>([]);
  const [similarHint, setSimilarHint] = useState<string | null>(null);
  const [similarLoading, setSimilarLoading] = useState(false);
  const [seasonSyncing, setSeasonSyncing] = useState(false);

  const numericId = Number(id);

  const fetchItem = async () => {
    setLoading(true);
    try {
      const basic = await getItem(numericId);
      setPlaybackPosition(basic.code === 200 && basic.data?.playbackPosition != null ? Number(basic.data.playbackPosition) || 0 : 0);

      const res = await getItemDetail(numericId);
      if (res.code === 200) {
        const item = res.data as MediaDetailItem;
        setData(item);
        if (item?.type === 'TV_SHOW') {
          const seasonsRes = await getItemSeasons(numericId);
          if (seasonsRes.code === 200) setSeasons(seasonsRes.data || []);
        } else {
          setSeasons([]);
        }
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    fetchItem();
    checkFavorite(numericId)
      .then((res) => {
        if (res?.code === 200) setIsFavorited(!!res.data?.favorite);
      })
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    getTags().then((res) => {
      if (res.code === 200) setAllTags(res.data || []);
    });
  }, []);

  useEffect(() => {
    if (!id) return;
    setSimilarLoading(true);
    setSimilarHint(null);
    getSimilarItems(numericId, 12)
      .then((res) => {
        if (res.code !== 200) return;
        const payload = res.data;
        if (Array.isArray(payload)) {
          setSimilarItems(payload);
          return;
        }
        const scored = payload?.scoredItems || [];
        setSimilarItems(scored.length > 0 ? scored.map((score) => score.item).filter(Boolean) : payload?.items || []);
        if (payload?.hint) setSimilarHint(payload.hint);
        else if (res.message && res.message !== 'success') setSimilarHint(res.message);
      })
      .catch(() => {})
      .finally(() => setSimilarLoading(false));
  }, [id]);

  const handleToggleFavorite = async () => {
    const res = await toggleFavorite(numericId);
    if (res?.code === 200) {
      const favorite = !!res.data?.favorite;
      setIsFavorited(favorite);
      message.success(favorite ? '已收藏' : '已取消收藏');
    }
  };

  const handleEditSave = async () => {
    const values = await editForm.validateFields();
    const res = await updateMetadata(numericId, values);
    if (res.code === 200) {
      message.success('元数据更新成功');
      setEditVisible(false);
      fetchItem();
    }
  };

  const handleRefresh = async () => {
    await refreshMetadata(numericId);
    message.success('元数据刷新已触发');
    setTimeout(fetchItem, 2000);
  };

  const handleClassify = async () => {
    const res = await classifyItem(numericId);
    if (res?.code === 200) {
      message.success('分类规则已执行');
      fetchItem();
    }
  };

  const handleDelete = async () => {
    await deleteItem(numericId);
    message.success('已移入回收站，可在回收站中恢复');
    history.push('/browse');
  };

  const handleDeleteFile = async () => {
    await deleteSourceFile(numericId);
    message.success('源文件已删除');
    fetchItem();
  };

  const handleAddTag = async (tagId: number) => {
    setAddingTag(true);
    try {
      const res = await addTagToItem(numericId, tagId);
      if (res?.code === 200) {
        message.success('已添加标签');
        setAddTagSelectValue(undefined);
        fetchItem();
      } else {
        message.error(res?.message || '添加失败');
      }
    } finally {
      setAddingTag(false);
    }
  };

  const handleRemoveTag = async (tagId: number) => {
    const res = await removeTagFromItem(numericId, tagId);
    if (res?.code === 200) {
      message.success('已移除标签');
      fetchItem();
    } else {
      message.error(res?.message || '移除失败');
    }
  };

  const handleSyncSeasons = async () => {
    setSeasonSyncing(true);
    try {
      const res = await syncTvSeasons(numericId);
      if (res?.code === 200) {
        message.success(`已同步 ${res.data?.seasonCount ?? 0} 季`);
        const seasonsRes = await getItemSeasons(numericId);
        if (seasonsRes.code === 200) setSeasons(seasonsRes.data || []);
      } else {
        message.error(res?.message || '同步失败');
      }
    } catch {
      message.error('同步季集失败');
    } finally {
      setSeasonSyncing(false);
    }
  };

  if (loading) return <div className="detail-loading"><Spin size="large" /></div>;
  if (!data) return <Result status="404" title="未找到媒体记录" />;

  const posterUrl = resolveItemPosterUrl({
    itemId: data.id,
    posterPath: data.posterPath,
    type: data.type,
    fileIds: data.fileIds,
    thumbnailWidth: 400,
  });
  const backdropUrl = resolveItemBackdropUrl({
    itemId: data.id,
    backdropPath: data.backdropPath,
    posterPath: data.posterPath,
    type: data.type,
    fileIds: data.fileIds,
    thumbnailWidth: 400,
  });
  const year = data.releaseDate ? data.releaseDate.substring(0, 4) : null;
  const vm = buildMetadata(data);
  const overviewFields = vm.overview.filter((field) => field.value != null && field.value !== '');
  const metadataFields = vm.metadata.filter((field) => field.value != null && field.value !== '');

  const renderBadges = () => {
    const badges: React.ReactNode[] = [];
    if (data.rating && data.rating > 0) badges.push(<span key="rating" className="detail-badge badge-rating">★ {data.rating.toFixed(1)}</span>);
    badges.push(<span key="type" className="detail-badge badge-type">{TYPE_LABELS[data.type] || data.type}</span>);
    if (data.movieMetadata?.runtimeMinutes) badges.push(<span key="runtime" className="detail-badge badge-runtime">{fmtDur(data.movieMetadata.runtimeMinutes * 60)}</span>);
    if (data.movieMetadata?.certification) badges.push(<span key="cert" className="detail-badge badge-runtime">{data.movieMetadata.certification}</span>);
    if (data.tvShowMetadata?.network) badges.push(<span key="network" className="detail-badge badge-runtime">{data.tvShowMetadata.network}</span>);
    if (data.tvShowMetadata?.status) badges.push(<span key="tv-status" className="detail-badge badge-runtime">{data.tvShowMetadata.status}</span>);
    if (data.imageMetadata?.width && data.imageMetadata?.height) badges.push(<span key="dim" className="detail-badge badge-runtime">{data.imageMetadata.width}x{data.imageMetadata.height}</span>);
    if (data.audioMetadata?.artist) badges.push(<span key="artist" className="detail-badge badge-runtime">{data.audioMetadata.artist}</span>);
    badges.push(
      <span key="status" className={`detail-badge badge-status ${data.status === 'IDENTIFIED' ? 'identified' : 'unidentified'}`}>
        {data.status === 'IDENTIFIED' ? '已识别' : data.status === 'UNIDENTIFIED' ? '未识别' : data.status}
      </span>,
    );
    return badges;
  };

  const renderPrimaryAction = () => {
    if (data.type === 'IMAGE') {
      return (
        <Button
          type="primary"
          size="large"
          icon={<EyeOutlined />}
          onClick={() => {
            if (data.fileIds?.length) window.open(getRawImageUrl(data.fileIds[0]), '_blank');
          }}
        >
          查看原图
        </Button>
      );
    }
    if (['MOVIE', 'TV_SHOW', 'AUDIO'].includes(data.type) && access.canPlayMedia) {
      return (
        <Button
          type="primary"
          size="large"
          icon={<PlayCircleFilled />}
          onClick={() => {
            const t = playbackPosition && playbackPosition > 30 ? playbackPosition : 0;
            history.push(`/player/${data.id}${t > 0 ? `?t=${t}` : ''}`);
          }}
        >
          {playbackPosition && playbackPosition > 30 ? '继续观看' : '播放'}
        </Button>
      );
    }
    return null;
  };

  const renderOverviewTab = () => (
    <div className="detail-overview-tab">
      {data.overview && (
        <div className="detail-synopsis">
          <h3 className="detail-section-title">简介</h3>
          <p className="detail-synopsis-text">{data.overview}</p>
        </div>
      )}
      {overviewFields.length > 0 && (
        <div className="detail-overview-grid">
          {overviewFields.map((field) => (
            <div key={field.label} className="detail-overview-card">
              {field.icon && <span className="overview-card-icon">{field.icon}</span>}
              <div>
                <div className="overview-card-label">{field.label}</div>
                <div className="overview-card-value">{field.value}</div>
              </div>
            </div>
          ))}
        </div>
      )}
      <div className="detail-tags-section">
        <h3 className="detail-section-title">标签与分类</h3>
        <div className="detail-tags">
          {data.tags?.map((tag) => (
            <Tag
              key={tag.id}
              color={tag.color || 'blue'}
              closable={access.canAssignTags}
              onClose={access.canAssignTags ? () => handleRemoveTag(tag.id) : undefined}
            >
              {tag.name}
            </Tag>
          ))}
          {data.categories?.map((category: CategoryItem) => <Tag key={`cat-${category.id}`}>{category.name}</Tag>)}
        </div>
        {access.canAssignTags && (
          <div style={{ marginTop: 8 }}>
            <TagSelect
              allTags={allTags}
              assignedTagIds={(data.tags || []).map((tag) => tag.id)}
              loading={addingTag}
              value={addTagSelectValue}
              onChange={setAddTagSelectValue}
              onSelect={handleAddTag}
            />
          </div>
        )}
      </div>
      {(similarItems.length > 0 || similarHint) && (
        <div style={{ marginTop: 24 }}>
          <HorizontalMediaRow title="相似推荐" items={similarItems} loading={similarLoading} />
          {similarHint && similarItems.length === 0 && (
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
              {similarHint}
            </Typography.Text>
          )}
        </div>
      )}
      {data.type === 'TV_SHOW' && (
        <div style={{ marginTop: 16 }}>
          <h3 className="detail-section-title">季与集</h3>
          <TvSeasonPanel
            mediaItemId={data.id}
            seasons={seasons}
            canSync={access.canRefreshMetadata}
            syncing={seasonSyncing}
            onSync={handleSyncSeasons}
          />
        </div>
      )}
    </div>
  );

  const renderMetadataTab = () => (
    <div className="detail-metadata-grid">
      {metadataFields.map((field) => (
        <div key={field.label} className="detail-meta-item">
          <span className="detail-meta-label">{field.label}</span>
          <span className="detail-meta-value">{field.value}</span>
        </div>
      ))}
      {metadataFields.length === 0 && <div style={{ color: 'var(--color-text-tertiary)' }}>暂无更多元数据</div>}
    </div>
  );

  const renderFilesTab = () => {
    if (!data.files || data.files.length === 0) {
      return <div style={{ color: 'var(--color-text-tertiary)', padding: '16px 0' }}>暂无文件信息</div>;
    }

    return (
      <div className="detail-files-list">
        {data.files.map((file: MediaFile) => (
          <div key={file.id} className="detail-file-card">
            <div className="file-header">
              <HddOutlined className="file-icon" />
              <span className="file-name">{file.fileName}</span>
            </div>
            {file.filePath && (
              <div className="file-path-row">
                <span className="spec-label">完整路径</span>
                <Typography.Text copyable={{ text: file.filePath }} ellipsis={{ tooltip: file.filePath }} style={{ display: 'block', marginTop: 4 }}>
                  {file.filePath}
                </Typography.Text>
              </div>
            )}
            <div className="file-specs">
              <div className="file-spec-item"><span className="spec-label">大小</span><span className="spec-value">{fmtSize(file.fileSize)}</span></div>
              {file.mimeType && <div className="file-spec-item"><span className="spec-label">格式</span><span className="spec-value">{file.mimeType}</span></div>}
              {file.videoCodec && <div className="file-spec-item"><span className="spec-label">视频编码</span><span className="spec-value">{file.videoCodec}</span></div>}
              {file.audioCodec && <div className="file-spec-item"><span className="spec-label">音频编码</span><span className="spec-value">{file.audioCodec}</span></div>}
              {file.width && file.height && <div className="file-spec-item"><span className="spec-label">分辨率</span><span className="spec-value">{file.width}x{file.height}</span></div>}
              {file.durationSeconds && <div className="file-spec-item"><span className="spec-label">时长</span><span className="spec-value">{fmtDur(file.durationSeconds)}</span></div>}
              {file.bitrate && <div className="file-spec-item"><span className="spec-label">码率</span><span className="spec-value">{(file.bitrate / 1000).toFixed(0)} kbps</span></div>}
            </div>
          </div>
        ))}
        <div className="detail-file-actions">
          {access.canDeleteSourceFile && (
            <Popconfirm title="此操作将删除源文件，不可恢复。确定继续？" onConfirm={handleDeleteFile}>
              <Button danger size="small" icon={<DeleteOutlined />}>删除源文件</Button>
            </Popconfirm>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="detail-page">
      <button className="detail-back" onClick={() => history.push('/browse')}>
        <ArrowLeftOutlined /> 返回
      </button>

      <div className="detail-hero">
        {backdropUrl && <div className="detail-hero-bg" style={{ backgroundImage: `url(${backdropUrl})` }} />}
        <div className="detail-hero-gradient" />
        <div className="detail-hero-content">
          <div className="detail-poster">
            {posterUrl ? <img src={posterUrl} alt={data.title} /> : <div className="detail-poster-placeholder">{TYPE_ICONS[data.type] || <FileOutlined />}</div>}
          </div>
          <div className="detail-hero-info">
            <h1 className="detail-title">
              {data.title}
              {year && <span className="detail-year">({year})</span>}
            </h1>
            <div className="detail-badges">{renderBadges()}</div>
            {data.overview && <div className="detail-overview-brief">{data.overview}</div>}
            <div className="detail-actions">
              {renderPrimaryAction()}
              <Button icon={isFavorited ? <HeartFilled /> : <HeartOutlined />} onClick={handleToggleFavorite} danger={isFavorited}>
                {isFavorited ? '已收藏' : '收藏'}
              </Button>
              {access.canEditMetadata && (
                <Button
                  icon={<EditOutlined />}
                  onClick={() => {
                    editForm.setFieldsValue({
                      title: data.title,
                      originalTitle: data.originalTitle,
                      overview: data.overview,
                      rating: data.rating,
                      network: data.tvShowMetadata?.network,
                      tvStatus: data.tvShowMetadata?.status,
                    });
                    setEditVisible(true);
                  }}
                >
                  编辑
                </Button>
              )}
              {access.canRefreshMetadata && <Button icon={<ReloadOutlined />} onClick={handleRefresh}>刷新元数据</Button>}
              {access.canEditMetadata && <Button onClick={handleClassify}>自动分类</Button>}
              {access.canEditMetadata && <Button onClick={() => setIdentifyVisible(true)}>手动匹配</Button>}
              {access.canDeleteMedia && (
                <Popconfirm title="确定删除此媒体项？" onConfirm={handleDelete}>
                  <Button danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="detail-body">
        <Tabs
          defaultActiveKey="overview"
          items={[
            { key: 'overview', label: '概览', children: renderOverviewTab() },
            { key: 'metadata', label: '元数据', children: renderMetadataTab() },
            { key: 'files', label: `文件${data.files?.length ? ` (${data.files.length})` : ''}`, children: renderFilesTab() },
          ]}
        />
      </div>

      {access.canEditMetadata && (
        <IdentifyModal itemId={numericId} open={identifyVisible} onClose={() => setIdentifyVisible(false)} onSuccess={fetchItem} />
      )}

      <Modal title="编辑元数据" open={editVisible} onCancel={() => setEditVisible(false)} onOk={handleEditSave} width={560}>
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}><Input /></Form.Item>
          <Form.Item name="originalTitle" label="原标题"><Input /></Form.Item>
          <Form.Item name="overview" label="简介"><Input.TextArea rows={4} /></Form.Item>
          <Form.Item name="rating" label="评分"><InputNumber min={0} max={10} step={0.1} style={{ width: 120 }} /></Form.Item>
          {data.type === 'TV_SHOW' && (
            <>
              <Form.Item name="network" label="电视台"><Input /></Form.Item>
              <Form.Item name="tvStatus" label="播出状态"><Input placeholder="Continuing / Ended" /></Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default MediaDetail;
