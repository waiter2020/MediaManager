import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button } from 'antd';
import { AppstoreAddOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import EmptyState from '@/components/EmptyState';
import HorizontalMediaRow from '@/components/HorizontalMediaRow';
import UnifiedSearchBox from '@/components/UnifiedSearchBox';
import { getDiscover } from '@/services/discover';
import type { MediaItem } from '@/types/media';
import './index.css';

const DiscoverPage: React.FC = () => {
  const access = useAccess();
  const [continueWatching, setContinueWatching] = useState<MediaItem[]>([]);
  const [watchlist, setWatchlist] = useState<MediaItem[]>([]);
  const [recommended, setRecommended] = useState<MediaItem[]>([]);
  const [favorites, setFavorites] = useState<MediaItem[]>([]);
  const [topRated, setTopRated] = useState<MediaItem[]>([]);
  const [unwatched, setUnwatched] = useState<MediaItem[]>([]);
  const [recentlyAdded, setRecentlyAdded] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const discoverRes = await getDiscover(24);
        if (discoverRes?.code === 200 && discoverRes.data) {
          setContinueWatching(discoverRes.data.continueWatching || []);
          setWatchlist(discoverRes.data.watchlist || []);
          setRecommended(discoverRes.data.recommended || []);
          setFavorites(discoverRes.data.favorites || []);
          setTopRated(discoverRes.data.topRated || []);
          setUnwatched(discoverRes.data.unwatched || []);
          setRecentlyAdded(discoverRes.data.recentlyAdded || []);
        }
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const empty =
    !loading &&
    continueWatching.length === 0 &&
    watchlist.length === 0 &&
    recommended.length === 0 &&
    favorites.length === 0 &&
    topRated.length === 0 &&
    unwatched.length === 0 &&
    recentlyAdded.length === 0;

  return (
    <PageContainer
      title="发现"
      extra={
        <Button icon={<AppstoreAddOutlined />} onClick={() => history.push('/collections')}>
          合集
        </Button>
      }
    >
      <UnifiedSearchBox className="discover-search" placeholder="搜索媒体，或输入自然语言条件" />

      {continueWatching.length > 0 && (
        <HorizontalMediaRow
          title="继续观看"
          items={continueWatching}
          loading={loading}
          playMode="resume"
        />
      )}
      {watchlist.length > 0 && (
        <HorizontalMediaRow title="Watchlist" items={watchlist} loading={loading} />
      )}
      {recommended.length > 0 && (
        <HorizontalMediaRow title="为你推荐" items={recommended} loading={loading} />
      )}
      {favorites.length > 0 && (
        <HorizontalMediaRow title="最近收藏" items={favorites} loading={loading} />
      )}
      {topRated.length > 0 && (
        <HorizontalMediaRow title="高分内容" items={topRated} loading={loading} />
      )}
      {unwatched.length > 0 && (
        <HorizontalMediaRow title="还没看" items={unwatched} loading={loading} />
      )}
      {recentlyAdded.length > 0 && (
        <HorizontalMediaRow title="最近添加" items={recentlyAdded} loading={loading} />
      )}
      {empty && (
        <EmptyState
          description="暂无推荐内容。请先确保账号有可访问的媒体库并完成扫描；播放几项内容后，会出现继续观看和相似推荐。"
          actionText={access.canManageLibrary ? '管理媒体库' : '浏览媒体'}
          onAction={() => history.push(access.canManageLibrary ? '/libraries' : '/browse')}
        />
      )}
    </PageContainer>
  );
};

export default DiscoverPage;
