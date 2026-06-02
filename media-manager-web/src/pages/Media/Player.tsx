import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Result, Segmented, Spin } from 'antd';
import { history, useParams, useSearchParams } from '@umijs/max';
import React, { useEffect, useState } from 'react';
import { ArrowLeftOutlined } from '@ant-design/icons';
import VideoPlayer from '@/components/VideoPlayer';
import { getItem } from '@/services/media';
import { appendAuthToken, getPlaybackInfo, getRawImageUrl, resolveItemPosterUrl } from '@/services/stream';
import { recordPlay } from '@/services/userActivity';
import type { MediaItem } from '@/types/media';

interface TranscodeTelemetry {
  speed: number;
  fps: number;
  time: string;
  status: string;
}

const Player: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const [data, setData] = useState<MediaItem | null>(null);
  const [streamUrl, setStreamUrl] = useState('');
  const [playbackMode, setPlaybackMode] = useState<'direct' | 'hls'>('direct');
  const [fileId, setFileId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [playError, setPlayError] = useState<string | null>(null);
  const [transcodeSpeed, setTranscodeSpeed] = useState<TranscodeTelemetry | null>(null);

  const resolveFileId = (item: { fileIds?: number[] }, override?: string | null) => {
    const parsed = override ? Number(override) : NaN;
    if (Number.isFinite(parsed) && item.fileIds?.includes(parsed)) {
      return parsed;
    }
    return Array.isArray(item.fileIds) && item.fileIds.length > 0 ? item.fileIds[0] : null;
  };

  useEffect(() => {
    const fetchItem = async () => {
      setLoading(true);
      setPlayError(null);
      try {
        const res = await getItem(Number(id));
        if (res.code !== 200 || !res.data) {
          setData(null);
          setStreamUrl('');
          setPlayError(res.message || '未找到媒体或该条目已失效，请返回媒体库重新选择');
          return;
        }

        const item = res.data;
        setData(item);
        const startSec = Number(searchParams.get('t'));
        const resumeAt = Number.isFinite(startSec) && startSec > 0 ? startSec : 0;
        recordPlay({ mediaItemId: item.id, position: resumeAt || undefined }).catch(() => {});

        const resolvedId = resolveFileId(item, searchParams.get('fileId'));
        if (resolvedId == null) {
          setStreamUrl('');
          setPlayError('该媒体项没有可播放文件，请在媒体库中重新扫描或选择其他条目');
          return;
        }
        setFileId(resolvedId);

        if (item.type === 'IMAGE') {
          setStreamUrl(getRawImageUrl(resolvedId));
          setPlaybackMode('direct');
          return;
        }

        try {
          const playback = await getPlaybackInfo(resolvedId);
          if (playback.code === 200 && playback.data?.url) {
            setPlaybackMode(playback.data.mode === 'hls' ? 'hls' : 'direct');
            setStreamUrl(appendAuthToken(playback.data.url));
          } else {
            setPlayError(playback.message || '无法获取播放地址，请检查文件路径映射与 FFmpeg。');
          }
        } catch (e: unknown) {
          const err = e as { response?: { data?: { message?: string } }; message?: string };
          const msg = err.response?.data?.message || err.message;
          setPlayError(
            msg ||
              '播放接口失败：若使用 Docker，请配置 MEDIAMANAGER_STORAGE_PATH_MAP 将宿主机路径映射到容器内。',
          );
        }
      } finally {
        setLoading(false);
      }
    };
    if (id) fetchItem();
  }, [id, searchParams]);

  // Real-time transcoding speed telemetry polling
  useEffect(() => {
    if (playbackMode !== 'hls' || fileId == null) {
      setTranscodeSpeed(null);
      return undefined;
    }
    const interval = setInterval(async () => {
      try {
        const resp = await fetch(`/api/v1/stream/${fileId}/transcode-speed`);
        const json = await resp.json();
        if (json.code === 200 && json.data) {
          setTranscodeSpeed(json.data);
        }
      } catch (ignored) {}
    }, 3000);
    return () => clearInterval(interval);
  }, [playbackMode, fileId]);

  const handleModeChange = (mode: 'direct' | 'hls') => {
    if (fileId == null) return;
    setPlaybackMode(mode);
    if (mode === 'direct') {
      setStreamUrl(appendAuthToken(`/api/v1/stream/raw/${fileId}`));
    } else {
      setStreamUrl(appendAuthToken(`/api/v1/stream/${fileId}/hls/master.m3u8`));
    }
  };

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  }
  if (!data) return <Result status="404" title="未找到媒体记录" />;

  const renderPlayer = () => {
    if (playError) return <Result status="error" title="无法播放" subTitle={playError} />;
    if (!streamUrl) {
      return <Result status="warning" title="无可用文件" subTitle="该媒体项没有关联的可播放文件" />;
    }

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
            当前浏览器不支持 audio 标签。
          </audio>
        </div>
      );
    }

    const poster =
      resolveItemPosterUrl({
        itemId: data.id,
        posterPath: data.posterPath,
        type: data.type,
        fileIds: data.fileIds,
      }) ?? undefined;
    const startSec = Number(searchParams.get('t'));
    const startTime = Number.isFinite(startSec) && startSec > 0 ? startSec : 0;
    return (
      <>
        <VideoPlayer
          src={streamUrl}
          mode={playbackMode}
          poster={poster}
          startTime={startTime}
          onError={setPlayError}
          onProgress={(seconds) => {
            recordPlay({ mediaItemId: data.id, position: seconds }).catch(() => {});
          }}
        />
        <div
          style={{
            padding: '16px 24px',
            background: 'var(--color-bg-elevated)',
            borderTop: '1px solid var(--color-border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <span style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>播放源格式:</span>
            <Segmented
              options={[
                { label: '原画直连 (Direct Play)', value: 'direct' },
                { label: '兼容转码 (HLS Transcode)', value: 'hls' },
              ]}
              value={playbackMode}
              onChange={(val) => handleModeChange(val as 'direct' | 'hls')}
            />
          </div>
          {playbackMode === 'hls' && transcodeSpeed && (
            <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                <span style={{
                  display: 'inline-block',
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  marginRight: 6,
                  background: transcodeSpeed.status === 'active'
                    ? '#10b981'
                    : transcodeSpeed.status === 'starting'
                      ? '#f59e0b'
                      : '#6b7280',
                  boxShadow: transcodeSpeed.status === 'active'
                    ? '0 0 8px #10b981, 0 0 16px rgba(16, 185, 129, 0.4)'
                    : 'none',
                  animation: transcodeSpeed.status === 'active'
                    ? 'pulse-glow 1.5s ease-in-out infinite'
                    : 'none',
                }} />
                转码状态:{' '}
                <span
                  style={{
                    color: transcodeSpeed.status === 'active' ? 'var(--color-accent-green)' : 'var(--color-accent-gold)',
                    fontWeight: 600,
                  }}
                >
                  {transcodeSpeed.status === 'active'
                    ? '转码中'
                    : transcodeSpeed.status === 'starting'
                      ? '初始化'
                      : transcodeSpeed.status === 'idle'
                        ? '就绪'
                        : transcodeSpeed.status}
                </span>
              </span>
              {transcodeSpeed.speed > 0 && (
                <span>
                  | 速度:{' '}
                  <span style={{ color: 'var(--color-primary-hover)', fontWeight: 600 }}>
                    {transcodeSpeed.speed.toFixed(2)}x
                  </span>
                </span>
              )}
              {transcodeSpeed.fps > 0 && <span>| 帧率: {transcodeSpeed.fps.toFixed(0)} fps</span>}
              {transcodeSpeed.time && transcodeSpeed.time !== '00:00:00' && (
                <span>| 已转码: {transcodeSpeed.time}</span>
              )}
            </div>
          )}
        </div>
      </>
    );
  };

  return (
    <>
    <style>{`@keyframes pulse-glow { 0%, 100% { opacity: 1; box-shadow: 0 0 8px #10b981, 0 0 16px rgba(16, 185, 129, 0.4); } 50% { opacity: 0.6; box-shadow: 0 0 4px #10b981, 0 0 8px rgba(16, 185, 129, 0.2); } }`}</style>
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
    </>
  );
};

export default Player;
