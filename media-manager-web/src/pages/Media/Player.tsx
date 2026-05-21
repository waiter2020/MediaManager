import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Result, Spin } from 'antd';
import { useParams, history } from '@umijs/max';
import React, { useEffect, useState } from 'react';
import { getItem } from '@/services/media';
import { getPlaybackInfo, appendAuthToken, getRawImageUrl } from '@/services/stream';
import { recordPlay } from '@/services/userActivity';
import { ArrowLeftOutlined } from '@ant-design/icons';
import VideoPlayer from '@/components/VideoPlayer';

const Player: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<any>(null);
  const [streamUrl, setStreamUrl] = useState<string>('');
  const [playbackMode, setPlaybackMode] = useState<'direct' | 'hls'>('direct');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchItem = async () => {
      setLoading(true);
      try {
        const res = await getItem(Number(id));
        if (res.code === 200) {
          const item = res.data;
          setData(item);
          recordPlay({ mediaItemId: item.id }).catch(() => {});
          if (Array.isArray(item.fileIds) && item.fileIds.length > 0) {
            const fileId = item.fileIds[0];
            if (item.type === 'IMAGE') {
              setStreamUrl(getRawImageUrl(fileId));
              setPlaybackMode('direct');
            } else if (item.type === 'AUDIO') {
              const pb = await getPlaybackInfo(fileId);
              if (pb.code === 200 && pb.data?.url) {
                setStreamUrl(appendAuthToken(pb.data.url));
              }
              setPlaybackMode('direct');
            } else {
              const pb = await getPlaybackInfo(fileId);
              if (pb.code === 200 && pb.data) {
                setPlaybackMode(pb.data.mode === 'hls' ? 'hls' : 'direct');
                setStreamUrl(appendAuthToken(pb.data.url));
              }
            }
          }
        }
      } finally {
        setLoading(false);
      }
    };
    if (id) fetchItem();
  }, [id]);

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  if (!data) return <Result status="404" title="未找到媒体记录" />;

  const renderPlayer = () => {
    if (!streamUrl) return <Result status="warning" title="无可用文件" />;

    if (data.type === 'IMAGE') {
      return (
        <div style={{ textAlign: 'center', padding: 16 }}>
          <img src={streamUrl} alt={data.title} style={{ maxHeight: '80vh', maxWidth: '100%' }} />
        </div>
      );
    }

    if (data.type === 'AUDIO') {
      return (
        <div style={{ padding: '40px 16px', textAlign: 'center' }}>
          <div style={{ marginBottom: 24, fontSize: 20, fontWeight: 500 }}>{data.title}</div>
          <audio controls autoPlay style={{ width: '100%', maxWidth: 600 }} src={streamUrl}>
            您的浏览器不支持 audio 标签。
          </audio>
        </div>
      );
    }

    const token = localStorage.getItem('accessToken') || '';
    const poster =
      data.id && token
        ? `/api/v1/items/${data.id}/poster?token=${encodeURIComponent(token)}`
        : data.posterPath;
    return <VideoPlayer src={streamUrl} mode={playbackMode} poster={poster} />;
  };

  return (
    <PageContainer
      title={data.title}
      extra={[
        <Button key="back" icon={<ArrowLeftOutlined />} onClick={() => history.push(`/media/${id}`)}>
          返回详情
        </Button>,
      ]}
    >
      <Card bodyStyle={{ padding: 0 }}>{renderPlayer()}</Card>
    </PageContainer>
  );
};

export default Player;
