import React, { useEffect, useState } from 'react';
import {
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  FolderOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { getItems } from '@/services/media';
import HorizontalMediaRow from '@/components/HorizontalMediaRow';
import EmptyState from '@/components/EmptyState';
import './index.css';

interface SystemInfo {
  totalMediaItems?: number;
  totalLibraries?: number;
  totalUsers?: number;
  videoCount?: number;
  imageCount?: number;
  audioCount?: number;
  tagCount?: number;
  version?: string;
}

interface RecentMediaItem {
  id: number;
  title: string;
  type?: string;
  posterPath?: string | null;
  fileIds?: number[];
  rating?: number | null;
  releaseDate?: string | null;
  overview?: string | null;
}

const STAT_CARDS = [
  { key: 'video', icon: <VideoCameraOutlined />, label: '视频', cls: 'stat-video', field: 'videoCount', fallback: 'totalMediaItems' },
  { key: 'image', icon: <PictureOutlined />, label: '图片', cls: 'stat-image', field: 'imageCount' },
  { key: 'audio', icon: <CustomerServiceOutlined />, label: '音频', cls: 'stat-audio', field: 'audioCount' },
  { key: 'library', icon: <FolderOutlined />, label: '媒体库', cls: 'stat-library', field: 'totalLibraries' },
  { key: 'tag', icon: <TagsOutlined />, label: '标签', cls: 'stat-tag', field: 'tagCount' },
];

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<SystemInfo | null>(null);
  const [recentItems, setRecentItems] = useState<RecentMediaItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [infoRes, recentRes] = await Promise.all([
          fetch('/api/v1/system/info', {
            headers: { Authorization: `Bearer ${localStorage.getItem('accessToken')}` },
          }).then((r) => r.json()),
          getItems({ page: 1, size: 20 }),
        ]);

        if (infoRes.code === 200) setStats(infoRes.data);
        if (recentRes?.code === 200) setRecentItems(recentRes.data?.items || []);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const getStatValue = (card: typeof STAT_CARDS[number]) => {
    if (!stats) return 0;
    const val = (stats as any)[card.field];
    if (val !== undefined && val !== null) return val;
    if (card.fallback) return (stats as any)[card.fallback] || 0;
    return 0;
  };

  return (
    <div className="dashboard-page">
      <h2>仪表盘</h2>

      {/* Stats */}
      <div className="stat-cards">
        {loading
          ? Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="stat-card-skeleton">
                <div className="skeleton-circle" />
                <div className="skeleton-number" />
                <div className="skeleton-label" />
              </div>
            ))
          : STAT_CARDS.map((card) => (
              <div key={card.key} className={`stat-card ${card.cls}`}>
                <div className="stat-icon">{card.icon}</div>
                <div className="stat-value">{getStatValue(card)}</div>
                <div className="stat-label">{card.label}</div>
              </div>
            ))}
      </div>

      {/* Recent items */}
      <div className="recent-section">
        <HorizontalMediaRow
          title="最近添加"
          items={recentItems}
          viewAllLink="/browse"
          loading={loading}
        />
      </div>

      {!loading && recentItems.length === 0 && (
        <EmptyState
          description="还没有媒体内容，添加媒体库开始管理你的媒体吧"
          actionText="添加媒体库"
          onAction={() => history.push('/libraries/create')}
        />
      )}
    </div>
  );
};

export default Dashboard;
