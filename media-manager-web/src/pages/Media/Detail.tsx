import React, { useEffect, useState } from 'react';
import { Button, Tag, Spin, Result, Modal, Form, Input, InputNumber, Tabs, Popconfirm, message, Typography, Select } from 'antd';
import { useParams, history } from '@umijs/max';
import {
  PlayCircleFilled,
  EditOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  FileOutlined,
  ClockCircleOutlined,
  CameraOutlined,
  AudioOutlined,
  HddOutlined,
  ArrowLeftOutlined,
  HeartOutlined,
  HeartFilled,
} from '@ant-design/icons';
import { getItemDetail, updateMetadata, refreshMetadata, deleteItem, deleteSourceFile, identifyItem, searchTmdbCandidates } from '@/services/media';
import { getTags, addTagToItem, removeTagFromItem } from '@/services/classification';
import { getRawImageUrl } from '@/services/stream';
import { toggleFavorite, checkFavorite } from '@/services/userActivity';
import './Detail.css';

const TYPE_LABELS: Record<string, string> = { MOVIE: '电影', TV_SHOW: '剧集', IMAGE: '图片', AUDIO: '音频' };
const TYPE_ICONS: Record<string, React.ReactNode> = {
  MOVIE: <VideoCameraOutlined />,
  TV_SHOW: <VideoCameraOutlined />,
  IMAGE: <PictureOutlined />,
  AUDIO: <CustomerServiceOutlined />,
};

interface MetaField { label: string; value: string | undefined; icon?: React.ReactNode }

// --- helpers ---
const fmtSize = (b: number) => {
  if (b >= 1073741824) return `${(b / 1073741824).toFixed(1)} GB`;
  if (b >= 1048576) return `${(b / 1048576).toFixed(1)} MB`;
  return `${(b / 1024).toFixed(1)} KB`;
};
const fmtDur = (sec: number) => {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m} 分钟`;
};

// --- ViewModel builders ---
function buildMovieVM(data: any): { overview: MetaField[]; metadata: MetaField[] } {
  const m = data.movieMetadata;
  const overview: MetaField[] = [
    { label: '类型', value: m?.genres },
    { label: '片长', value: m?.runtimeMinutes ? fmtDur(m.runtimeMinutes * 60) : undefined, icon: <ClockCircleOutlined /> },
    { label: '分级', value: m?.certification },
    { label: '评分', value: data.rating > 0 ? `${data.rating.toFixed(1)} / 10` : undefined },
  ];
  const metadata: MetaField[] = [
    { label: '原始标题', value: data.originalTitle },
    { label: '宣传语', value: m?.tagline },
    { label: '制片厂', value: m?.studios },
    { label: '发行日期', value: data.releaseDate },
    { label: '添加时间', value: data.createdAt },
    { label: '状态', value: data.status },
  ];
  return { overview, metadata };
}

function buildImageVM(data: any): { overview: MetaField[]; metadata: MetaField[] } {
  const img = data.imageMetadata;
  const overview: MetaField[] = [
    { label: '尺寸', value: img?.width && img?.height ? `${img.width} × ${img.height}` : undefined, icon: <PictureOutlined /> },
    { label: '相机', value: img?.cameraMake ? `${img.cameraMake} ${img.cameraModel || ''}`.trim() : undefined, icon: <CameraOutlined /> },
    { label: '镜头', value: img?.lens },
    { label: '拍摄时间', value: img?.takenAt, icon: <ClockCircleOutlined /> },
  ];
  const metadata: MetaField[] = [
    { label: 'ISO', value: img?.iso != null ? String(img.iso) : undefined },
    { label: '光圈', value: img?.aperture },
    { label: '快门速度', value: img?.shutterSpeed },
    { label: 'GPS 纬度', value: img?.gpsLatitude != null ? String(img.gpsLatitude) : undefined },
    { label: 'GPS 经度', value: img?.gpsLongitude != null ? String(img.gpsLongitude) : undefined },
    { label: '发行日期', value: data.releaseDate },
    { label: '添加时间', value: data.createdAt },
    { label: '状态', value: data.status },
  ];
  return { overview, metadata };
}

function buildAudioVM(data: any): { overview: MetaField[]; metadata: MetaField[] } {
  const a = data.audioMetadata;
  const overview: MetaField[] = [
    { label: '艺术家', value: a?.artist, icon: <AudioOutlined /> },
    { label: '专辑', value: a?.album },
    { label: '时长', value: a?.durationSeconds > 0 ? fmtDur(a.durationSeconds) : undefined, icon: <ClockCircleOutlined /> },
    { label: '类型', value: a?.genres },
  ];
  const metadata: MetaField[] = [
    { label: '专辑艺术家', value: a?.albumArtist },
    { label: '曲目号', value: a?.trackNumber != null ? String(a.trackNumber) : undefined },
    { label: '碟号', value: a?.discNumber != null ? String(a.discNumber) : undefined },
    { label: '比特率', value: a?.bitrate ? `${a.bitrate} kbps` : undefined },
    { label: '采样率', value: a?.sampleRate ? `${a.sampleRate} Hz` : undefined },
    { label: '声道', value: a?.channels != null ? String(a.channels) : undefined },
    { label: '发行日期', value: data.releaseDate },
    { label: '添加时间', value: data.createdAt },
    { label: '状态', value: data.status },
  ];
  return { overview, metadata };
}

function buildGenericVM(data: any): { overview: MetaField[]; metadata: MetaField[] } {
  return {
    overview: [],
    metadata: [
      { label: '原始标题', value: data.originalTitle },
      { label: '发行日期', value: data.releaseDate },
      { label: '添加时间', value: data.createdAt },
      { label: '状态', value: data.status },
    ],
  };
}

// --- component ---
const MediaDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [editVisible, setEditVisible] = useState(false);
  const [editForm] = Form.useForm();
  const [isFavorited, setIsFavorited] = useState(false);
  const [allTags, setAllTags] = useState<any[]>([]);
  const [addingTag, setAddingTag] = useState(false);
  const [addTagSelectValue, setAddTagSelectValue] = useState<number | undefined>(undefined);
  const [identifyVisible, setIdentifyVisible] = useState(false);
  const [identifyForm] = Form.useForm();
  const [tmdbCandidates, setTmdbCandidates] = useState<any[]>([]);
  const [tmdbSearchLoading, setTmdbSearchLoading] = useState(false);

  const fetchItem = async () => {
    setLoading(true);
    try {
      const res = await getItemDetail(Number(id));
      if (res.code === 200) setData(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchItem();
      checkFavorite(Number(id)).then((res) => {
        if (res?.code === 200) setIsFavorited(res.data?.favorited || false);
      }).catch(() => {});
    }
  }, [id]);

  useEffect(() => {
    getTags().then((res) => {
      if (res.code === 200) setAllTags(res.data || []);
    });
  }, []);

  const handleToggleFavorite = async () => {
    const res = await toggleFavorite(Number(id));
    if (res?.code === 200) {
      setIsFavorited(res.data?.favorited || false);
      message.success(res.data?.favorited ? '已收藏' : '已取消收藏');
    }
  };

  const handleEditSave = async () => {
    const values = await editForm.validateFields();
    const res = await updateMetadata(Number(id), values);
    if (res.code === 200) { message.success('元数据更新成功'); setEditVisible(false); fetchItem(); }
  };
  const handleRefresh = async () => { await refreshMetadata(Number(id)); message.success('元数据刷新已触发'); setTimeout(fetchItem, 2000); };
  const handleTmdbSearch = async (q: string) => {
    if (!q?.trim()) {
      setTmdbCandidates([]);
      return;
    }
    setTmdbSearchLoading(true);
    try {
      const res = await searchTmdbCandidates(Number(id), q.trim());
      if (res.code === 200) setTmdbCandidates(res.data || []);
    } finally {
      setTmdbSearchLoading(false);
    }
  };

  const handleIdentify = async () => {
    const values = await identifyForm.validateFields();
    const res = await identifyItem(Number(id), { provider: 'tmdb', externalId: String(values.externalId) });
    if (res.code === 200) {
      message.success('手动匹配成功');
      setIdentifyVisible(false);
      fetchItem();
    }
  };
  const handleDelete = async () => {
    await deleteItem(Number(id));
    message.success('已移入回收站，可在回收站中恢复');
    history.push('/browse');
  };
  const handleDeleteFile = async () => { await deleteSourceFile(Number(id)); message.success('源文件已删除'); fetchItem(); };

  const handleAddTag = async (tagId: number) => {
    setAddingTag(true);
    try {
      const res = await addTagToItem(Number(id), tagId);
      if (res?.code === 200) {
        message.success('已添加标签');
        setAddTagSelectValue(undefined);
        fetchItem();
      } else message.error(res?.message || '添加失败');
    } finally {
      setAddingTag(false);
    }
  };

  const handleRemoveTag = async (tagId: number) => {
    try {
      const res = await removeTagFromItem(Number(id), tagId);
      if (res?.code === 200) { message.success('已移除标签'); fetchItem(); }
      else message.error(res?.message || '移除失败');
    } catch (e) {
      message.error('移除失败');
    }
  };

  if (loading) return <div className="detail-loading"><Spin size="large" /></div>;
  if (!data) return <Result status="404" title="未找到媒体记录" />;

  // --- poster / backdrop with stream fallback ---
  const token = localStorage.getItem('accessToken') || '';
  const tokenParam = `token=${encodeURIComponent(token)}`;
  const posterUrl = data.posterPath
    ? `/api/v1/items/${data.id}/poster?${tokenParam}`
    : data.type === 'IMAGE' && data.fileIds?.length > 0
      ? `/api/v1/stream/images/${data.fileIds[0]}?w=400&${tokenParam}`
      : null;
  const backdropUrl = data.backdropPath ? `/api/v1/items/${data.id}/backdrop?${tokenParam}` : posterUrl;
  const year = data.releaseDate ? data.releaseDate.substring(0, 4) : null;

  // --- ViewModel ---
  const vm =
    data.type === 'MOVIE' || data.type === 'TV_SHOW' ? buildMovieVM(data)
    : data.type === 'IMAGE' ? buildImageVM(data)
    : data.type === 'AUDIO' ? buildAudioVM(data)
    : buildGenericVM(data);

  const overviewFields = vm.overview.filter(f => f.value);
  const metadataFields = vm.metadata.filter(f => f.value);

  // --- hero badges ---
  const renderBadges = () => {
    const badges: React.ReactNode[] = [];
    if (data.rating > 0) badges.push(<span key="r" className="detail-badge badge-rating">★ {data.rating.toFixed(1)}</span>);
    badges.push(<span key="t" className="detail-badge badge-type">{TYPE_LABELS[data.type] || data.type}</span>);
    if (data.movieMetadata?.runtimeMinutes > 0)
      badges.push(<span key="rt" className="detail-badge badge-runtime">{fmtDur(data.movieMetadata.runtimeMinutes * 60)}</span>);
    if (data.movieMetadata?.certification)
      badges.push(<span key="cert" className="detail-badge badge-runtime">{data.movieMetadata.certification}</span>);
    if (data.imageMetadata?.width && data.imageMetadata?.height)
      badges.push(<span key="dim" className="detail-badge badge-runtime">{data.imageMetadata.width}×{data.imageMetadata.height}</span>);
    if (data.audioMetadata?.artist)
      badges.push(<span key="artist" className="detail-badge badge-runtime">{data.audioMetadata.artist}</span>);
    badges.push(
      <span key="s" className={`detail-badge badge-status ${data.status === 'IDENTIFIED' ? 'identified' : 'unidentified'}`}>
        {data.status === 'IDENTIFIED' ? '已识别' : data.status === 'UNIDENTIFIED' ? '未识别' : data.status}
      </span>
    );
    return badges;
  };

  // --- primary action by type ---
  const renderPrimaryAction = () => {
    if (data.type === 'IMAGE') {
      return (
        <Button type="primary" size="large" icon={<EyeOutlined />}
          onClick={() => { if (data.fileIds?.length > 0) window.open(getRawImageUrl(data.fileIds[0]), '_blank'); }}>
          查看原图
        </Button>
      );
    }
    if (['MOVIE', 'TV_SHOW', 'AUDIO'].includes(data.type)) {
      return (
        <Button type="primary" size="large" icon={<PlayCircleFilled />}
          onClick={() => history.push(`/player/${data.id}`)}>
          播放
        </Button>
      );
    }
    return null;
  };

  // --- Tab: overview ---
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
          {overviewFields.map(f => (
            <div key={f.label} className="detail-overview-card">
              {f.icon && <span className="overview-card-icon">{f.icon}</span>}
              <div>
                <div className="overview-card-label">{f.label}</div>
                <div className="overview-card-value">{f.value}</div>
              </div>
            </div>
          ))}
        </div>
      )}
      <div className="detail-tags-section">
        <h3 className="detail-section-title">标签 & 分类</h3>
        <div className="detail-tags">
          {data.tags?.map((t: any) => (
            <Tag
              key={t.id}
              color={t.color || 'blue'}
              closable
              onClose={() => handleRemoveTag(t.id)}
            >
              {t.name}
            </Tag>
          ))}
          {data.categories?.map((c: any) => <Tag key={`cat-${c.id}`}>{c.name}</Tag>)}
        </div>
        <div style={{ marginTop: 8 }}>
          <Select
            placeholder="添加标签"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 200 }}
            loading={addingTag}
            value={addTagSelectValue}
            onChange={(tagId: number) => {
              if (tagId != null) handleAddTag(tagId);
              else setAddTagSelectValue(undefined);
            }}
            options={allTags
              .filter((t) => !data.tags?.some((ct: any) => ct.id === t.id))
              .map((t) => ({ label: t.name, value: t.id }))}
          />
        </div>
      </div>
    </div>
  );

  // --- Tab: metadata ---
  const renderMetadataTab = () => (
    <div className="detail-metadata-grid">
      {metadataFields.map(f => (
        <div key={f.label} className="detail-meta-item">
          <span className="detail-meta-label">{f.label}</span>
          <span className="detail-meta-value">{f.value}</span>
        </div>
      ))}
      {metadataFields.length === 0 && <div style={{ color: 'var(--color-text-tertiary)' }}>暂无更多元数据</div>}
    </div>
  );

  // --- Tab: files ---
  const renderFilesTab = () => {
    if (!data.files || data.files.length === 0)
      return <div style={{ color: 'var(--color-text-tertiary)', padding: '16px 0' }}>暂无文件信息</div>;

    return (
      <div className="detail-files-list">
        {data.files.map((f: any) => (
          <div key={f.id} className="detail-file-card">
            <div className="file-header">
              <HddOutlined className="file-icon" />
              <span className="file-name">{f.fileName}</span>
            </div>
            {f.filePath && (
              <div className="file-path-row">
                <span className="spec-label">完整路径</span>
                <Typography.Text copyable={{ text: f.filePath }} ellipsis={{ tooltip: f.filePath }} style={{ display: 'block', marginTop: 4 }}>
                  {f.filePath}
                </Typography.Text>
              </div>
            )}
            <div className="file-specs">
              {f.fileSize > 0 && <div className="file-spec-item"><span className="spec-label">大小</span><span className="spec-value">{fmtSize(f.fileSize)}</span></div>}
              {f.mimeType && <div className="file-spec-item"><span className="spec-label">格式</span><span className="spec-value">{f.mimeType}</span></div>}
              {f.videoCodec && <div className="file-spec-item"><span className="spec-label">视频编码</span><span className="spec-value">{f.videoCodec}</span></div>}
              {f.audioCodec && <div className="file-spec-item"><span className="spec-label">音频编码</span><span className="spec-value">{f.audioCodec}</span></div>}
              {f.width > 0 && f.height > 0 && <div className="file-spec-item"><span className="spec-label">分辨率</span><span className="spec-value">{f.width}×{f.height}</span></div>}
              {f.durationSeconds > 0 && <div className="file-spec-item"><span className="spec-label">时长</span><span className="spec-value">{fmtDur(f.durationSeconds)}</span></div>}
              {f.bitrate > 0 && <div className="file-spec-item"><span className="spec-label">码率</span><span className="spec-value">{(f.bitrate / 1000).toFixed(0)} kbps</span></div>}
            </div>
          </div>
        ))}
        <div className="detail-file-actions">
          <Popconfirm title="此操作将删除源文件，不可恢复！确定继续？" onConfirm={handleDeleteFile}>
            <Button danger size="small" icon={<DeleteOutlined />}>删除源文件</Button>
          </Popconfirm>
        </div>
      </div>
    );
  };

  return (
    <div className="detail-page">
      {/* Back link */}
      <button className="detail-back" onClick={() => history.push('/browse')}>
        <ArrowLeftOutlined /> 返回
      </button>

      {/* Hero */}
      <div className="detail-hero">
        {backdropUrl && <div className="detail-hero-bg" style={{ backgroundImage: `url(${backdropUrl})` }} />}
        <div className="detail-hero-gradient" />
        <div className="detail-hero-content">
          <div className="detail-poster">
            {posterUrl ? (
              <img src={posterUrl} alt={data.title} />
            ) : (
              <div className="detail-poster-placeholder">
                {TYPE_ICONS[data.type] || <FileOutlined />}
              </div>
            )}
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
              <Button
                icon={isFavorited ? <HeartFilled /> : <HeartOutlined />}
                onClick={handleToggleFavorite}
                danger={isFavorited}
              >
                {isFavorited ? '已收藏' : '收藏'}
              </Button>
              <Button icon={<EditOutlined />} onClick={() => {
                editForm.setFieldsValue({ title: data.title, originalTitle: data.originalTitle, overview: data.overview, rating: data.rating });
                setEditVisible(true);
              }}>编辑</Button>
              <Button icon={<ReloadOutlined />} onClick={handleRefresh}>刷新元数据</Button>
              <Button onClick={() => setIdentifyVisible(true)}>TMDb 匹配</Button>
              <Popconfirm title="确定删除此媒体项？" onConfirm={handleDelete}>
                <Button danger icon={<DeleteOutlined />}>删除</Button>
              </Popconfirm>
            </div>
          </div>
        </div>
      </div>

      {/* Body */}
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

      <Modal
        title="TMDb 手动匹配"
        open={identifyVisible}
        onCancel={() => { setIdentifyVisible(false); setTmdbCandidates([]); }}
        onOk={handleIdentify}
        width={560}
      >
        <Form form={identifyForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="搜索影片">
            <Input.Search
              placeholder="输入片名搜索"
              loading={tmdbSearchLoading}
              onSearch={handleTmdbSearch}
              enterButton="搜索"
            />
          </Form.Item>
          {tmdbCandidates.length > 0 && (
            <Form.Item label="选择匹配结果">
              <Select
                placeholder="从搜索结果选择"
                options={tmdbCandidates.map((c) => ({
                  value: String(c.id),
                  label: `${c.title}${c.releaseDate ? ` (${c.releaseDate})` : ''}`,
                }))}
                onChange={(v) => identifyForm.setFieldValue('externalId', v)}
              />
            </Form.Item>
          )}
          <Form.Item name="externalId" label="TMDb ID" rules={[{ required: true, message: '请选择或输入 TMDb ID' }]}>
            <Input placeholder="例如 27205" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit modal */}
      <Modal title="编辑元数据" open={editVisible} onCancel={() => setEditVisible(false)} onOk={handleEditSave} width={560}>
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="originalTitle" label="原始标题"><Input /></Form.Item>
          <Form.Item name="overview" label="简介"><Input.TextArea rows={4} /></Form.Item>
          <Form.Item name="rating" label="评分"><InputNumber min={0} max={10} step={0.1} style={{ width: 120 }} /></Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MediaDetail;
