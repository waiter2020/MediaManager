import { PageContainer } from '@ant-design/pro-components';
import { Card, Result, Space, Button, Spin, Image } from 'antd';
import { useParams, history } from '@umijs/max';
import React, { useEffect, useState } from 'react';
import { getItem } from '@/services/media';
import { getFileStreamUrl, getRawImageUrl } from '@/services/stream';
import { recordPlay } from '@/services/userActivity';
import { ArrowLeftOutlined } from '@ant-design/icons';

const Player: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<any>(null);
  const [streamUrl, setStreamUrl] = useState<string>('');
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
            if (item.type === 'IMAGE') {
              setStreamUrl(getRawImageUrl(item.fileIds[0]));
            } else {
              setStreamUrl(getFileStreamUrl(item.fileIds[0]));
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
          <Image
            src={streamUrl}
            alt={data.title}
            style={{ maxHeight: '80vh', maxWidth: '100%' }}
          />
        </div>
      );
    }

    if (data.type === 'AUDIO') {
      return (
        <div style={{ padding: '40px 16px', textAlign: 'center' }}>
          <div style={{ marginBottom: 24, fontSize: 20, fontWeight: 500 }}>{data.title}</div>
          {data.audioMetadata?.artist && (
            <div style={{ marginBottom: 16, color: '#888' }}>{data.audioMetadata.artist}</div>
          )}
          <audio controls autoPlay style={{ width: '100%', maxWidth: 600 }} src={streamUrl}>
            您的浏览器不支持 audio 标签。
          </audio>
        </div>
      );
    }

    return (
      <div style={{ background: '#000', borderRadius: 8, overflow: 'hidden' }}>
        <video
          width="100%"
          controls
          autoPlay
          style={{ maxHeight: '80vh', display: 'block' }}
          src={streamUrl}
        >
          您的浏览器不支持 video 标签。
        </video>
      </div>
    );
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
      <Card bodyStyle={{ padding: 0 }}>
        {renderPlayer()}
      </Card>
    </PageContainer>
  );
};

export default Player;
