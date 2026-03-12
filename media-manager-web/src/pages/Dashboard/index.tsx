import React, { useEffect, useState } from 'react';
import {
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  FolderOutlined,
  TagsOutlined,
  SyncOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { getItems } from '@/services/media';
import { getSystemInfo } from '@/services/system';
import { getRecentPlayed, getRecentFavorites } from '@/services/userActivity';
import HorizontalMediaRow from '@/components/HorizontalMediaRow';
import EmptyState from '@/components/EmptyState';
import type { ScanProgress } from '@/models/global';
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
  { key: 'video', icon: <VideoCameraOutlined />, label: '视频', cls: 'stat-video', field: 'videoCount', fallback: 'totalMediaItems', link: '/browse' },
  { key: 'image', icon: <PictureOutlined />, label: '图片', cls: 'stat-image', field: 'imageCount', link: '/browse' },
  { key: 'audio', icon: <CustomerServiceOutlined />, label: '音频', cls: 'stat-audio', field: 'audioCount', link: '/browse' },
  { key: 'library', icon: <FolderOutlined />, label: '媒体库', cls: 'stat-library', field: 'totalLibraries', link: '/libraries' },
  { key: 'tag', icon: <TagsOutlined />, label: '标签', cls: 'stat-tag', field: 'tagCount', link: '/classification/tags' },
];

function formatNumber(n: number): string {
  return n.toLocaleString('zh-CN');
}


const ScanSummaryStatCard: React.FC<{ scans: Record<number, ScanProgress> }> = ({ scans }) => {
  const activeCount = Object.keys(scans).length;
  const hasActive = activeCount > 0;

  return (
    <div className={`stat-card stat-scan${hasActive ? ' stat-scan-active' : ''}`}>
      <div className="stat-icon">
        {hasActive ? <SyncOutlined spin /> : <CheckCircleOutlined />}
      </div>
      <div className="stat-value">{hasActive ? activeCount : '--'}</div>
      <div className="stat-label">{hasActive ? '扫描中' : '扫描空闲'}</div>
    </div>
  );
};


const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<SystemInfo | null>(null);
  const [recentItems, setRecentItems] = useState<RecentMediaItem[]>([]);
  const [recentPlayedItems, setRecentPlayedItems] = useState<RecentMediaItem[]>([]);
  const [recentFavoriteItems, setRecentFavoriteItems] = useState<RecentMediaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const { scanStatus } = useModel('global');

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [infoRes, recentRes, playedRes, favRes] = await Promise.all([
          getSystemInfo(),
          getItems({ page: 1, size: 20 }),
          getRecentPlayed({ limit: 20 }).catch(() => null),
          getRecentFavorites({ limit: 20 }).catch(() => null),
        ]);

        if (infoRes?.code === 200) setStats(infoRes.data);
        if (recentRes?.code === 200) setRecentItems(recentRes.data?.items || []);
        if (playedRes?.code === 200) setRecentPlayedItems(playedRes.data || []);
        if (favRes?.code === 200) setRecentFavoriteItems(favRes.data || []);
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
    if ((card as any).fallback) return (stats as any)[(card as any).fallback] || 0;
    return 0;
  };

  return (
    <div className="dashboard-page">
      <h2>仪表盘</h2>

      <div className="stat-cards">
        {loading
          ? Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="stat-card-skeleton">
                <div className="skeleton-circle" />
                <div className="skeleton-number" />
                <div className="skeleton-label" />
              </div>
            ))
          : (<>
              {STAT_CARDS.map((card) => (
                <div
                  key={card.key}
                  className={`stat-card ${card.cls}`}
                  onClick={() => card.link && history.push(card.link)}
                  role="button"
                  tabIndex={0}
                >
                  <div className="stat-icon">{card.icon}</div>
                  <div className="stat-value">{formatNumber(getStatValue(card))}</div>
                  <div className="stat-label">{card.label}</div>
                </div>
              ))}
              <ScanSummaryStatCard scans={scanStatus} />
            </>)}
      </div>

      <div className="dashboard-main">
        <div className="recent-section">
          <HorizontalMediaRow
            title="最近添加"
            items={recentItems}
            viewAllLink="/browse"
            loading={loading}
          />
        </div>

        {recentPlayedItems.length > 0 && (
          <div className="recent-section">
            <HorizontalMediaRow
              title="最近播放"
              items={recentPlayedItems}
              loading={loading}
            />
          </div>
        )}

        {recentFavoriteItems.length > 0 && (
          <div className="recent-section">
            <HorizontalMediaRow
              title="最近收藏"
              items={recentFavoriteItems}
              loading={loading}
            />
          </div>
        )}

        {!loading && recentItems.length === 0 && (
          <EmptyState
            description="还没有媒体内容，添加媒体库开始管理你的媒体吧"
            actionText="添加媒体库"
            onAction={() => history.push('/libraries/create')}
          />
        )}
      </div>
    </div>
  );
};

export default Dashboard;
