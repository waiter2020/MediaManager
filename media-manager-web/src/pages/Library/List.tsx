import React, { useEffect, useState } from 'react';
import { Button, Popconfirm, message, Tooltip } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
  SyncOutlined,
  EditOutlined,
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  AppstoreOutlined,
  FolderOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { getLibraries, deleteLibrary, triggerScan } from '@/services/library';
import EmptyState from '@/components/EmptyState';
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

const LibraryList: React.FC = () => {
  const [libraries, setLibraries] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchLibraries = async () => {
    setLoading(true);
    try {
      const res = await getLibraries();
      if (res.code === 200) setLibraries(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLibraries();
  }, []);

  const handleDelete = async (e: React.MouseEvent, id: number) => {
    e.stopPropagation();
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

  if (!loading && libraries.length === 0) {
    return (
      <div className="library-page">
        <div className="library-page-header">
          <h2>媒体库</h2>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/libraries/create')}>
            添加媒体库
          </Button>
        </div>
        <EmptyState
          description="还没有媒体库，创建一个开始管理你的媒体文件吧"
          actionText="添加媒体库"
          onAction={() => history.push('/libraries/create')}
        />
      </div>
    );
  }

  return (
    <div className="library-page">
      <div className="library-page-header">
        <h2>媒体库</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/libraries/create')}>
          添加媒体库
        </Button>
      </div>

      <div className="library-grid">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="library-card" style={{ minHeight: 200 }}>
                <div className="library-card-accent" style={{ background: '#222' }} />
                <div className="library-card-body">
                  <div style={{ height: 14, width: 120, background: '#1a1a2e', borderRadius: 6, marginBottom: 12 }} />
                  <div style={{ height: 10, width: 80, background: '#1a1a2e', borderRadius: 4, marginBottom: 20 }} />
                  <div style={{ height: 10, width: '100%', background: '#1a1a2e', borderRadius: 4 }} />
                </div>
              </div>
            ))
          : libraries.map((lib) => {
              const typeKey = lib.type || 'MIXED';
              return (
                <div
                  key={lib.id}
                  className="library-card"
                  onClick={() => history.push(`/libraries/${lib.id}`)}
                >
                  <div className={`library-card-accent type-${typeKey}`} />
                  <div className="library-card-body">
                    <div className="library-card-top">
                      <div className={`library-card-icon type-${typeKey}`}>
                        {TYPE_ICONS[typeKey] || <FolderOutlined />}
                      </div>
                      <div>
                        <div className="library-card-name">{lib.name}</div>
                        <div className="library-card-type">
                          {TYPE_LABELS[typeKey] || typeKey} · {lib.language || '默认'}
                        </div>
                      </div>
                    </div>

                    <div className="library-card-stats">
                      <div className="library-card-stat">
                        <span className="stat-val">{lib.paths?.length || 0}</span>
                        <span className="stat-lbl">路径</span>
                      </div>
                      {lib.autoScan && (
                        <div className="library-card-stat">
                          <span className="stat-val">{lib.scanIntervalMinutes || 30}</span>
                          <span className="stat-lbl">分钟/扫描</span>
                        </div>
                      )}
                    </div>

                    {lib.paths && lib.paths.length > 0 && (
                      <div className="library-card-paths">
                        {lib.paths.slice(0, 2).map((p: any, i: number) => (
                          <div key={i} className="library-card-path">
                            <FolderOutlined />
                            <span>{p.path}</span>
                          </div>
                        ))}
                        {lib.paths.length > 2 && (
                          <div className="library-card-path" style={{ color: 'var(--color-text-secondary)' }}>
                            +{lib.paths.length - 2} 个更多路径
                          </div>
                        )}
                      </div>
                    )}

                    <div className="library-card-actions">
                      <Tooltip title="扫描">
                        <Button
                          size="small"
                          icon={<SyncOutlined />}
                          onClick={(e) => handleScan(e, lib.id)}
                        />
                      </Tooltip>
                      <Tooltip title="编辑">
                        <Button
                          size="small"
                          icon={<EditOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            history.push(`/libraries/${lib.id}/edit`);
                          }}
                        />
                      </Tooltip>
                      <Tooltip title="查看详情">
                        <Button
                          size="small"
                          icon={<FolderOpenOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            history.push(`/libraries/${lib.id}`);
                          }}
                        />
                      </Tooltip>
                      <Popconfirm
                        title="确认删除此媒体库？"
                        onConfirm={(e) => handleDelete(e as any, lib.id)}
                        onCancel={(e) => e?.stopPropagation()}
                      >
                        <Tooltip title="删除">
                          <Button
                            size="small"
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
    </div>
  );
};

export default LibraryList;
