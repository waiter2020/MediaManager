import React, { useEffect, useState } from 'react';
import {
  CheckCircleOutlined,
  CustomerServiceOutlined,
  FolderOutlined,
  PictureOutlined,
  SyncOutlined,
  TagsOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import EmptyState from '@/components/EmptyState';
import HorizontalMediaRow from '@/components/HorizontalMediaRow';
import { PC_HORIZONTAL_ROW_AUTOPLAY_PROPS } from '@/constants/mediaPreview';
import SystemCapabilitiesCard from '@/components/SystemCapabilitiesCard';
import UnifiedSearchBox from '@/components/UnifiedSearchBox';
import { getDiscover } from '@/services/discover';
import { getLibraries } from '@/services/library';
import { getItems } from '@/services/media';
import { getSystemInfo, type SystemInfo } from '@/services/system';
import type { ScanProgress } from '@/models/global';
import type { MediaLibrary } from '@/types/library';
import type { MediaItem } from '@/types/media';
import './index.css';

type StatField = 'videoCount' | 'imageCount' | 'audioCount' | 'totalLibraries' | 'tagCount' | 'totalMediaItems';

const LIBRARY_ROW_ITEM_LIMIT = 20;

interface StatCard {
  key: string;
  icon: React.ReactNode;
  label: string;
  cls: string;
  field: StatField;
  fallback?: StatField;
  link: string;
}

interface LibraryMediaRow {
  library: MediaLibrary;
  items: MediaItem[];
}

const STAT_CARDS: StatCard[] = [
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
      <div className="stat-icon">{hasActive ? <SyncOutlined spin /> : <CheckCircleOutlined />}</div>
      <div className="stat-value">{hasActive ? activeCount : '--'}</div>
      <div className="stat-label">{hasActive ? '扫描中' : '扫描空闲'}</div>
    </div>
  );
};

const Dashboard: React.FC = () => {
  const access = useAccess();
  const [stats, setStats] = useState<SystemInfo | null>(null);
  const [recentItems, setRecentItems] = useState<MediaItem[]>([]);
  const [continueWatching, setContinueWatching] = useState<MediaItem[]>([]);
  const [libraryRows, setLibraryRows] = useState<LibraryMediaRow[]>([]);
  const [loading, setLoading] = useState(true);
  const { scanStatus } = useModel('global');

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [infoRes, discoverRes, librariesRes] = await Promise.all([
          getSystemInfo(),
          getDiscover(20).catch(() => null),
          getLibraries().catch(() => null),
        ]);

        if (infoRes?.code === 200) setStats(infoRes.data);
        if (discoverRes?.code === 200 && discoverRes.data) {
          setRecentItems(discoverRes.data.recentlyAdded || []);
          setContinueWatching(discoverRes.data.continueWatching || []);
        }
        if (librariesRes?.code === 200) {
          const rows = await Promise.all(
            (librariesRes.data || []).map(async (library) => {
              const itemsRes = await getItems({
                libraryId: library.id,
                page: 1,
                size: LIBRARY_ROW_ITEM_LIMIT,
                sortField: 'createdAt',
                sortOrder: 'desc',
              }).catch(() => null);

              return {
                library,
                items: itemsRes?.code === 200 ? itemsRes.data?.items || [] : [],
              };
            }),
          );
          setLibraryRows(rows.filter((row) => row.items.length > 0));
        } else {
          setLibraryRows([]);
        }
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const getStatValue = (card: StatCard) => {
    if (!stats) return 0;
    const value = stats[card.field];
    if (typeof value === 'number') return value;
    const fallbackValue = card.fallback ? stats[card.fallback] : undefined;
    return typeof fallbackValue === 'number' ? fallbackValue : 0;
  };

  return (
    <div className="dashboard-page">
      <h2>仪表盘</h2>
      <UnifiedSearchBox className="dashboard-search" placeholder="搜索媒体，或输入自然语言条件" />

      <div className="stat-cards">
        {loading ? (
          Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="stat-card-skeleton">
              <div className="skeleton-circle" />
              <div className="skeleton-number" />
              <div className="skeleton-label" />
            </div>
          ))
        ) : (
          <>
            {STAT_CARDS.map((card) => (
              <div
                key={card.key}
                className={`stat-card ${card.cls}`}
                onClick={() => history.push(card.link)}
                role="button"
                tabIndex={0}
              >
                <div className="stat-icon">{card.icon}</div>
                <div className="stat-value">{formatNumber(getStatValue(card))}</div>
                <div className="stat-label">{card.label}</div>
              </div>
            ))}
            <ScanSummaryStatCard scans={scanStatus} />
          </>
        )}
      </div>

      {access.canManageSystem && <SystemCapabilitiesCard />}

      <div className="dashboard-main">
        {continueWatching.length > 0 && (
          <HorizontalMediaRow
            title="继续观看"
            items={continueWatching}
            viewAllLink="/discover"
            playMode="resume"
            loading={loading}
            {...PC_HORIZONTAL_ROW_AUTOPLAY_PROPS}
          />
        )}
        <div className="recent-section">
          <HorizontalMediaRow
            title="最近添加"
            items={recentItems}
            viewAllLink="/browse"
            loading={loading}
            {...PC_HORIZONTAL_ROW_AUTOPLAY_PROPS}
          />
        </div>

        {libraryRows.length > 0 && (
          <div className="library-rows">
            {libraryRows.map((row) => (
              <HorizontalMediaRow
                key={row.library.id}
                title={row.library.name}
                items={row.items}
                viewAllLink={`/browse?libraryId=${row.library.id}`}
                loading={loading}
                {...PC_HORIZONTAL_ROW_AUTOPLAY_PROPS}
              />
            ))}
          </div>
        )}

        {!loading && recentItems.length === 0 && continueWatching.length === 0 && libraryRows.length === 0 && (
          <EmptyState
            description={
              stats?.hasViewableLibraries === false
                ? '当前账号没有可访问的媒体库。管理员请在用户管理中分配库权限。'
                : '还没有媒体内容。创建媒体库、配置路径并扫描后即可在此看到新内容。'
            }
            actionText={stats?.hasViewableLibraries === false ? undefined : '添加媒体库'}
            onAction={stats?.hasViewableLibraries === false ? undefined : () => history.push('/libraries/create')}
          />
        )}
      </div>
    </div>
  );
};

export default Dashboard;
