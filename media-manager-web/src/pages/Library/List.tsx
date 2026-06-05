import React, { useEffect, useMemo, useState } from 'react';
import { Button, Input, Popconfirm, Select, Tooltip, message } from 'antd';
import {
  ApiOutlined,
  AppstoreOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  PictureOutlined,
  PlusOutlined,
  SearchOutlined,
  SortAscendingOutlined,
  SyncOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import EmptyState from '@/components/EmptyState';
import { deleteLibrary, getLibraries, triggerScan } from '@/services/library';
import type { ScanProgress } from '@/models/global';
import type { LibraryPath, MediaLibrary } from '@/types/library';
import './List.css';

const TYPE_LABELS: Record<string, string> = {
  MOVIE: '电影库',
  TV_SHOW: '剧集库',
  IMAGE: '图片库',
  AUDIO: '音频库',
  MIXED: '混合库',
};

const TYPE_ICONS: Record<string, React.ReactNode> = {
  MOVIE: <VideoCameraOutlined />,
  TV_SHOW: <VideoCameraOutlined />,
  IMAGE: <PictureOutlined />,
  AUDIO: <CustomerServiceOutlined />,
  MIXED: <AppstoreOutlined />,
};

const SORT_OPTIONS = [
  { label: '名称 A-Z', value: 'name-asc' },
  { label: '名称 Z-A', value: 'name-desc' },
  { label: '最近创建', value: 'created-desc' },
  { label: '媒体数量', value: 'items-desc' },
];

function formatRelativeTime(isoStr?: string | null): string {
  if (!isoStr) return '从未';
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

function shortenPath(path: string, maxLen = 40): string {
  if (!path || path.length <= maxLen) return path || '';
  return '...' + path.slice(path.length - maxLen + 3);
}

const LibraryList: React.FC = () => {
  const [libraries, setLibraries] = useState<MediaLibrary[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [sortBy, setSortBy] = useState('name-asc');
  const { scanStatus } = useModel('global');
  const access = useAccess();

  const fetchLibraries = async () => {
    setLoading(true);
    try {
      const res = await getLibraries();
      if (res.code === 200) setLibraries(res.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLibraries();
  }, []);

  const handleDelete = async (e: React.MouseEvent | undefined, id: number) => {
    e?.stopPropagation();
    const res = await deleteLibrary(id);
    if (res.code === 200) {
      message.success('删除成功');
      fetchLibraries();
    }
  };

  const handleScan = async (e: React.MouseEvent, id: number) => {
    e.stopPropagation();
    const res = await triggerScan(id);
    if (res.code === 200) {
      message.success('扫描任务已触发');
    }
  };

  const filteredLibraries = useMemo(() => {
    let list = [...libraries];
    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase();
      list = list.filter(
        (lib) =>
          lib.name?.toLowerCase().includes(kw) ||
          lib.paths?.some((path: LibraryPath) => path.path?.toLowerCase().includes(kw)),
      );
    }

    const [field, order] = sortBy.split('-');
    list.sort((a, b) => {
      if (field === 'name') {
        return order === 'asc'
          ? (a.name || '').localeCompare(b.name || '')
          : (b.name || '').localeCompare(a.name || '');
      }
      if (field === 'created') {
        const ta = new Date(a.createdAt || 0).getTime();
        const tb = new Date(b.createdAt || 0).getTime();
        return order === 'desc' ? tb - ta : ta - tb;
      }
      if (field === 'items') {
        return order === 'desc'
          ? (b.totalItems || 0) - (a.totalItems || 0)
          : (a.totalItems || 0) - (b.totalItems || 0);
      }
      return 0;
    });
    return list;
  }, [libraries, keyword, sortBy]);

  const totalItems = libraries.reduce((sum, lib) => sum + (lib.totalItems || 0), 0);

  return (
    <div className="libraries-page">
      <div className="libraries-header">
        <div className="libraries-header-left">
          <h2>媒体库</h2>
          {!loading && libraries.length > 0 && (
            <div className="libraries-summary">
              <span className="summary-item">
                <FolderOutlined /> {libraries.length} 个库
              </span>
              <span className="summary-divider" />
              <span className="summary-item">
                <DatabaseOutlined /> {totalItems.toLocaleString('zh-CN')} 个媒体
              </span>
            </div>
          )}
        </div>
        {access.canManageLibrary && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/libraries/create')}>
            添加媒体库
          </Button>
        )}
      </div>

      {!loading && libraries.length > 0 && (
        <div className="libraries-toolbar">
          <div className="libraries-toolbar-left">
            <Input
              className="libraries-search"
              placeholder="搜索媒体库名称或路径..."
              prefix={<SearchOutlined style={{ color: 'rgba(255,255,255,0.3)' }} />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              allowClear
              style={{ width: 260 }}
            />
          </div>
          <div className="libraries-toolbar-right">
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

      <div className="libraries-content">
        {loading ? (
          <div className="libraries-grid">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="library-card library-card-skeleton">
                <div className="library-card-accent" />
                <div className="library-card-body">
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
                  <div className="skeleton-line w80" />
                  <div className="skeleton-line w50" />
                </div>
              </div>
            ))}
          </div>
        ) : filteredLibraries.length === 0 && keyword ? (
          <EmptyState description={`未找到与 "${keyword}" 相关的媒体库`} />
        ) : libraries.length === 0 ? (
          <EmptyState
            description="还没有媒体库，创建一个开始管理你的媒体文件"
            actionText={access.canManageLibrary ? '添加媒体库' : undefined}
            onAction={access.canManageLibrary ? () => history.push('/libraries/create') : undefined}
          />
        ) : (
          <div className="libraries-grid">
            {filteredLibraries.map((lib) => {
              const typeKey = lib.type || 'MIXED';
              const scan: ScanProgress | undefined = scanStatus[lib.id];
              const isScanning = !!scan;
              const scanPct =
                scan && scan.totalFiles > 0
                  ? Math.round((scan.scannedFiles / scan.totalFiles) * 100)
                  : 0;

              return (
                <div
                  key={lib.id}
                  className={`library-card ${isScanning ? 'scanning' : ''}`}
                  onClick={() => history.push(`/libraries/${lib.id}`)}
                >
                  <div className={`library-card-accent type-${typeKey}`}>
                    {isScanning && <div className="library-card-scan-bar" style={{ width: `${scanPct}%` }} />}
                  </div>
                  <div className="library-card-body">
                    <div className="library-card-top">
                      <div className={`library-card-icon type-${typeKey}`}>
                        {TYPE_ICONS[typeKey] || <FolderOutlined />}
                      </div>
                      <div className="library-card-info">
                        <div className="library-card-name">{lib.name}</div>
                        <div className="library-card-type">
                          {TYPE_LABELS[typeKey] || typeKey}
                          {lib.language ? ` / ${lib.language}` : ''}
                        </div>
                      </div>
                      {isScanning && (
                        <Tooltip title={`扫描中 ${scanPct}%`}>
                          <span className="library-card-scan-badge">
                            <SyncOutlined spin /> {scanPct}%
                          </span>
                        </Tooltip>
                      )}
                    </div>

                    <div className="library-card-stats">
                      <div className="library-card-stat">
                        <span className="stat-val">{(lib.totalItems || 0).toLocaleString('zh-CN')}</span>
                        <span className="stat-lbl">媒体</span>
                      </div>
                      <div className="library-card-stat">
                        <span className="stat-val">{lib.paths?.length || 0}</span>
                        <span className="stat-lbl">路径</span>
                      </div>
                      <div className="library-card-stat">
                        <span className="stat-val">
                          <ClockCircleOutlined style={{ fontSize: 12, marginRight: 3 }} />
                          {formatRelativeTime(lib.lastScannedAt)}
                        </span>
                        <span className="stat-lbl">上次扫描</span>
                      </div>
                    </div>

                    {lib.paths && lib.paths.length > 0 && (
                      <div className="library-card-paths">
                        {lib.paths.slice(0, 2).map((path, index) => (
                          <div key={`${path.path}-${index}`} className="library-card-path" title={path.path}>
                            <FolderOutlined />
                            <span>{shortenPath(path.path)}</span>
                          </div>
                        ))}
                        {lib.paths.length > 2 && (
                          <div className="library-card-path more">
                            +{lib.paths.length - 2} 个更多路径
                          </div>
                        )}
                      </div>
                    )}

                    <div className="library-card-actions">
                      {access.canScanLibrary && (
                        <Tooltip title="扫描">
                          <Button
                            size="small"
                            type="text"
                            icon={<SyncOutlined spin={isScanning} />}
                            onClick={(e) => handleScan(e, lib.id)}
                            disabled={isScanning}
                          />
                        </Tooltip>
                      )}
                      {access.canEditLibraryPlugins && (
                        <Tooltip title="插件配置">
                          <Button
                            size="small"
                            type="text"
                            icon={<ApiOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              history.push(`/libraries/${lib.id}/plugins`);
                            }}
                          />
                        </Tooltip>
                      )}
                      {access.canManageLibrary && (
                        <Tooltip title="编辑">
                          <Button
                            size="small"
                            type="text"
                            icon={<EditOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              history.push(`/libraries/${lib.id}/edit`);
                            }}
                          />
                        </Tooltip>
                      )}
                      <Tooltip title="查看详情">
                        <Button
                          size="small"
                          type="text"
                          icon={<FolderOpenOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            history.push(`/libraries/${lib.id}`);
                          }}
                        />
                      </Tooltip>
                      <div className="library-card-actions-spacer" />
                      {access.canDeleteLibrary && (
                        <Popconfirm
                          title="确认删除此媒体库？"
                          description="删除后该库的所有媒体条目也会被移除"
                          onConfirm={(e) => handleDelete(e as React.MouseEvent | undefined, lib.id)}
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
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default LibraryList;
