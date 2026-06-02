import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button } from 'antd';
import { CompassOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import EmptyState from '@/components/EmptyState';
import HorizontalMediaRow from '@/components/HorizontalMediaRow';
import UnifiedSearchBox from '@/components/UnifiedSearchBox';
import { getDiscover } from '@/services/discover';
import { getRecentFavorites } from '@/services/userActivity';
import type { MediaItem } from '@/types/media';
import './index.css';

const DiscoverPage: React.FC = () => {
  const access = useAccess();
  const [continueWatching, setContinueWatching] = useState<MediaItem[]>([]);
  const [recommended, setRecommended] = useState<MediaItem[]>([]);
  const [favorites, setFavorites] = useState<MediaItem[]>([]);
  const [recentlyAdded, setRecentlyAdded] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [discoverRes, favRes] = await Promise.all([
          getDiscover(24),
          getRecentFavorites({ limit: 24 }).catch(() => null),
        ]);
        if (discoverRes?.code === 200 && discoverRes.data) {
          setContinueWatching(discoverRes.data.continueWatching || []);
          setRecommended(discoverRes.data.recommended || []);
          setRecentlyAdded(discoverRes.data.recentlyAdded || []);
        }
        if (favRes?.code === 200) {
          setFavorites(favRes.data || []);
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
    recommended.length === 0 &&
    favorites.length === 0 &&
    recentlyAdded.length === 0;

  return (
    <PageContainer title="发现">
      <UnifiedSearchBox className="discover-search" placeholder="搜索媒体，或输入自然语言条件" />

      {/* 🚀 Premium Glassmorphic Hero Banner */}
      {!loading && (continueWatching.length > 0 || recommended.length > 0) && (
        <div className="discover-hero-banner">
          <div className="hero-glow-overlay" />
          <div className="hero-content">
            <div className="hero-tag">
              <CompassOutlined className="hero-tag-icon" />
              <span>智能探索专区</span>
            </div>
            <h1 className="hero-title">
              发现您的 <span className="text-neon-gradient">精彩视界</span>
            </h1>
            <p className="hero-description">
              整合深度 AI 学习技术，依据您的历史观影行为、收藏偏好与视频元数据属性，为您量身定制的专属私人影院。点击下方“开始探索”或卡片，即刻开启极致流畅的 HLS 无缝视听享受。
            </p>
            <div className="hero-actions">
              <Button
                type="primary"
                size="large"
                className="hero-btn-primary"
                icon={<PlayCircleOutlined />}
                onClick={() => {
                  const firstItem = continueWatching[0] || recommended[0];
                  if (firstItem) {
                    history.push(`/media/${firstItem.id}`);
                  }
                }}
              >
                开始探索
              </Button>
            </div>
          </div>
        </div>
      )}

      {continueWatching.length > 0 && (
        <HorizontalMediaRow
          title="继续观看"
          items={continueWatching}
          loading={loading}
          playMode="resume"
        />
      )}
      {recommended.length > 0 && (
        <HorizontalMediaRow title="为你推荐" items={recommended} loading={loading} />
      )}
      {recentlyAdded.length > 0 && (
        <HorizontalMediaRow title="最近添加" items={recentlyAdded} loading={loading} />
      )}
      {favorites.length > 0 && (
        <HorizontalMediaRow title="最近收藏" items={favorites} loading={loading} />
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
