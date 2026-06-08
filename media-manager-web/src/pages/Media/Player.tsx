import React, { useEffect, useMemo, useRef, useState } from 'react';
import { history, useParams, useSearchParams } from '@umijs/max';
import { ArrowLeftOutlined, CustomerServiceOutlined, PictureOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { Button, Result, Segmented, Select, Spin } from 'antd';
import VideoPlayer from '@/components/VideoPlayer';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { getItemDetail } from '@/services/media';
import {
  appendAuthToken,
  getPlaybackInfo,
  getRawImageUrl,
  getSubtitleTrackUrl,
  getTranscodeSpeed,
  resolveItemBackdropUrl,
  resolveItemPosterUrl,
  type PlaybackInfo,
  type PlaybackMode,
  type PlaybackModePreference,
  type PlaybackOption,
  type PlaybackQuality,
  type TranscodeMode,
  type TranscodeTelemetry,
} from '@/services/stream';
import { recordPlay } from '@/services/userActivity';
import type { MediaItem } from '@/types/media';
import './Player.css';

function resolveFileId(item: MediaItem, override?: string | null) {
  const ids = item.fileIds?.length ? item.fileIds : item.files?.map((file) => file.id) || [];
  const parsed = override ? Number(override) : NaN;
  if (Number.isFinite(parsed) && ids.includes(parsed)) {
    return parsed;
  }
  return ids[0] ?? null;
}

function formatQuality(playback: PlaybackInfo | null) {
  if (!playback) return '';
  const quality = playback.quality && playback.quality !== 'auto' ? playback.quality : '';
  const method = playback.playMethod || playback.mode;
  return [method, quality].filter(Boolean).join(' / ');
}

const fallbackQualityOptions: PlaybackOption[] = [
  { value: 'auto', label: '自动' },
  { value: 'source', label: '原画' },
  { value: '1080p', label: '1080p' },
  { value: '720p', label: '720p' },
  { value: '480p', label: '480p' },
  { value: '360p', label: '360p' },
];

const fallbackTranscodeOptions: PlaybackOption[] = [
  { value: 'auto', label: '自动' },
  { value: 'software', label: '软编码' },
  { value: 'hardware', label: '硬编码' },
];

function qualityLabel(value?: string) {
  if (!value || value === 'auto') return '自动';
  if (value === 'source') return '原画';
  return value;
}

function transcodeModeLabel(value?: string) {
  if (value === 'software') return '软编码';
  if (value === 'hardware') return '硬编码';
  return '自动';
}

function normalizeMode(value?: string | null): PlaybackModePreference {
  if (value === 'direct' || value === 'hls') return value;
  return 'auto';
}

function normalizeQuality(value?: string | null): PlaybackQuality {
  const allowed = ['auto', 'source', '2160p', '1080p', '720p', '480p', '360p'];
  return allowed.includes(value || '') ? (value as PlaybackQuality) : 'auto';
}

function normalizeTranscodeMode(value?: string | null): TranscodeMode {
  if (value === 'software' || value === 'hardware') return value;
  return 'auto';
}

const PlayerPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const [data, setData] = useState<MediaItem | null>(null);
  const [streamUrl, setStreamUrl] = useState('');
  const [playbackMode, setPlaybackMode] = useState<PlaybackMode>('direct');
  const [playbackPreference, setPlaybackPreference] = useState<PlaybackModePreference>(() =>
    normalizeMode(searchParams.get('mode')),
  );
  const [selectedQuality, setSelectedQuality] = useState<PlaybackQuality>(() =>
    normalizeQuality(searchParams.get('quality')),
  );
  const [selectedTranscodeMode, setSelectedTranscodeMode] = useState<TranscodeMode>(() =>
    normalizeTranscodeMode(searchParams.get('transcodeMode')),
  );
  const [playbackInfo, setPlaybackInfo] = useState<PlaybackInfo | null>(null);
  const [qualityOptions, setQualityOptions] = useState<PlaybackOption[]>(fallbackQualityOptions);
  const [transcodeModeOptions, setTranscodeModeOptions] =
    useState<PlaybackOption[]>(fallbackTranscodeOptions);
  const [fileId, setFileId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [playError, setPlayError] = useState<string | null>(null);
  const [transcodeSpeed, setTranscodeSpeed] = useState<TranscodeTelemetry | null>(null);
  const lastAudioReportRef = useRef(0);
  const autoplayDisabled = useIsMobileAutoplayDisabled();

  const numericId = Number(id);
  const startSec = Number(searchParams.get('t'));
  const startTime = Number.isFinite(startSec) && startSec > 0 ? startSec : 0;

  useEffect(() => {
    document.documentElement.classList.add('standalone-player-root');
    document.body.classList.add('standalone-player-body');
    return () => {
      document.documentElement.classList.remove('standalone-player-root');
      document.body.classList.remove('standalone-player-body');
    };
  }, []);

  const loadPlayback = async (
    nextFileId: number,
    preferredMode: PlaybackModePreference = playbackPreference,
    quality: PlaybackQuality = selectedQuality,
    transcodeMode: TranscodeMode = selectedTranscodeMode,
  ) => {
    const playback = await getPlaybackInfo(nextFileId, {
      mode: preferredMode,
      quality,
      transcodeMode,
    });
    if (playback.code === 200 && playback.data?.url) {
      setPlaybackInfo(playback.data);
      setPlaybackMode(playback.data.mode === 'hls' ? 'hls' : 'direct');
      setStreamUrl(appendAuthToken(playback.data.url));
      if (playback.data.qualities?.length) {
        setQualityOptions(playback.data.qualities);
      }
      if (playback.data.transcodeModes?.length) {
        setTranscodeModeOptions(playback.data.transcodeModes);
      }
      setPlayError(null);
    } else {
      setStreamUrl('');
      setPlaybackInfo(null);
      setPlayError(playback.message || '无法获取播放地址，请检查文件路径映射与 FFmpeg。');
    }
  };

  useEffect(() => {
    const fetchItem = async () => {
      setLoading(true);
      setPlayError(null);
      setPlaybackInfo(null);
      setTranscodeSpeed(null);
      try {
        const res = await getItemDetail(numericId);
        if (res.code !== 200 || !res.data) {
          setData(null);
          setStreamUrl('');
          setPlayError(res.message || '未找到媒体或该条目已失效，请返回媒体库重新选择。');
          return;
        }

        const item = res.data;
        setData(item);
        recordPlay({ mediaItemId: item.id, position: startTime || undefined }).catch(() => {});

        const resolvedId = resolveFileId(item, searchParams.get('fileId'));
        if (resolvedId == null) {
          setFileId(null);
          setStreamUrl('');
          setPlayError('该媒体项没有可播放文件，请在媒体库中重新扫描或选择其他条目。');
          return;
        }

        setFileId(resolvedId);
        if (item.type === 'IMAGE') {
          setPlaybackMode('direct');
          setStreamUrl(getRawImageUrl(resolvedId));
          return;
        }

        await loadPlayback(resolvedId);
      } catch (error: unknown) {
        const err = error as { response?: { data?: { message?: string } }; message?: string };
        setStreamUrl('');
        setPlayError(
          err.response?.data?.message ||
            err.message ||
            '播放接口失败：若使用 Docker，请配置 MEDIAMANAGER_STORAGE_PATH_MAP 将宿主机路径映射到容器内。',
        );
      } finally {
        setLoading(false);
      }
    };

    if (Number.isFinite(numericId)) {
      fetchItem();
    }
  }, [numericId, searchParams, startTime]);

  useEffect(() => {
    if (playbackMode !== 'hls' || fileId == null) {
      setTranscodeSpeed(null);
      return undefined;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const res = await getTranscodeSpeed(fileId, playbackInfo?.variant);
        if (!cancelled && res.code === 200 && res.data) {
          setTranscodeSpeed(res.data);
        }
      } catch {
        if (!cancelled) {
          setTranscodeSpeed(null);
        }
      }
    };

    poll();
    const interval = window.setInterval(poll, 3000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [playbackMode, fileId, playbackInfo?.variant]);

  const poster = useMemo(
    () =>
      data
        ? resolveItemPosterUrl({
            itemId: data.id,
            posterPath: data.posterPath,
            type: data.type,
            fileIds: data.fileIds,
          }) ?? undefined
        : undefined,
    [data],
  );

  const backdrop = useMemo(
    () =>
      data
        ? resolveItemBackdropUrl({
            itemId: data.id,
            backdropPath: data.backdropPath,
            posterPath: data.posterPath,
            type: data.type,
            fileIds: data.fileIds,
          }) ?? poster
        : undefined,
    [data, poster],
  );

  const subtitleTracks = useMemo(
    () =>
      (data?.subtitles || []).map((subtitle) => ({
        src: getSubtitleTrackUrl(subtitle.id),
        label: subtitle.title || subtitle.language || subtitle.fileName || `Subtitle ${subtitle.id}`,
        language: subtitle.language,
        defaultTrack: subtitle.defaultTrack,
      })),
    [data?.subtitles],
  );

  const handleModeChange = async (mode: PlaybackModePreference) => {
    if (fileId == null || data?.type === 'IMAGE') return;
    setPlaybackPreference(mode);
    if (mode === 'direct') {
      setPlaybackMode('direct');
    }
    setPlayError(null);
    try {
      await loadPlayback(fileId, mode);
    } catch {
      setPlayError(mode === 'hls' ? '无法启动兼容转码播放。' : '无法启动原画直连播放。');
    }
  };

  const handleQualityChange = async (quality: PlaybackQuality) => {
    setSelectedQuality(quality);
    if (fileId == null || data?.type === 'IMAGE') return;
    try {
      await loadPlayback(fileId, playbackPreference, quality, selectedTranscodeMode);
    } catch {
      setPlayError('无法切换到所选质量档位。');
    }
  };

  const handleTranscodeModeChange = async (mode: TranscodeMode) => {
    setSelectedTranscodeMode(mode);
    if (fileId == null || data?.type === 'IMAGE') return;
    try {
      await loadPlayback(fileId, playbackPreference, selectedQuality, mode);
    } catch {
      setPlayError('无法切换到所选编码方式。');
    }
  };

  const renderTranscodeStatus = () => {
    if (playbackMode !== 'hls') return null;
    const status = transcodeSpeed?.status || (playbackInfo?.transcoding ? 'starting' : 'idle');
    const active = status === 'active';
    const starting = status === 'starting';
    return (
      <div className="player-transcode-status">
        <span className={`player-transcode-dot${active ? ' active' : starting ? ' starting' : ''}`} />
        <span>
          转码状态 <strong>{active ? '转码中' : starting ? '初始化' : status === 'idle' ? '就绪' : status}</strong>
        </span>
        {transcodeSpeed?.speed ? <span>{transcodeSpeed.speed.toFixed(2)}x</span> : null}
        {transcodeSpeed?.fps ? <span>{transcodeSpeed.fps.toFixed(0)} fps</span> : null}
        {transcodeSpeed?.time && transcodeSpeed.time !== '00:00:00' ? <span>{transcodeSpeed.time}</span> : null}
      </div>
    );
  };

  const renderStage = () => {
    if (loading) {
      return (
        <div className="player-center">
          <Spin size="large" tip="正在加载播放信息..." />
        </div>
      );
    }

    if (!data) {
      return (
        <div className="player-center">
          <Result status="404" title="未找到媒体记录" subTitle={playError || undefined} />
        </div>
      );
    }

    if (playError) {
      return (
        <div className="player-center">
          <Result status="error" title="无法播放" subTitle={playError} />
        </div>
      );
    }

    if (!streamUrl) {
      return (
        <div className="player-center">
          <Result status="warning" title="无可用文件" subTitle="该媒体项没有关联的可播放文件。" />
        </div>
      );
    }

    if (data.type === 'IMAGE') {
      return (
        <div className="player-image-stage">
          <img src={streamUrl} alt={data.title} />
        </div>
      );
    }

    if (data.type === 'AUDIO') {
      return (
        <div className="player-audio-stage">
          {backdrop && <div className="player-audio-backdrop" style={{ backgroundImage: `url(${backdrop})` }} />}
          <div className="player-audio-content">
            {poster ? (
              <img className="player-audio-cover" src={poster} alt={data.title} />
            ) : (
              <div className="player-audio-cover placeholder">
                <CustomerServiceOutlined />
              </div>
            )}
            <div className="player-audio-title">{data.title}</div>
            <audio
              className="player-audio-control"
              controls
              autoPlay={!autoplayDisabled}
              src={streamUrl}
              onTimeUpdate={(event) => {
                const seconds = Math.floor(event.currentTarget.currentTime || 0);
                if (seconds > 0 && Math.abs(seconds - lastAudioReportRef.current) >= 15) {
                  lastAudioReportRef.current = seconds;
                  recordPlay({ mediaItemId: data.id, position: seconds }).catch(() => {});
                }
              }}
            >
              当前浏览器不支持 audio 标签。
            </audio>
          </div>
        </div>
      );
    }

    return (
      <div className="player-video-stage">
        <VideoPlayer
          src={streamUrl}
          mode={playbackMode}
          poster={poster}
          fill
          startTime={startTime}
          subtitles={subtitleTracks}
          onError={setPlayError}
          onProgress={(seconds) => {
            recordPlay({ mediaItemId: data.id, position: seconds }).catch(() => {});
          }}
        />
      </div>
    );
  };

  const canSwitchMode = Boolean(data && data.type !== 'IMAGE' && fileId != null);
  const typeIcon =
    data?.type === 'IMAGE' ? <PictureOutlined /> : data?.type === 'AUDIO' ? <CustomerServiceOutlined /> : <VideoCameraOutlined />;

  return (
    <div className="player-page">
      <div className="player-toolbar">
        <div className="player-toolbar-main">
          <Button
            className="player-icon-button"
            icon={<ArrowLeftOutlined />}
            aria-label="返回详情"
            onClick={() => history.push(id ? `/media/${id}` : '/browse')}
          />
          <div className="player-heading">
            <div className="player-title">
              {typeIcon} {data?.title || 'MediaManager Player'}
            </div>
            <div className="player-subtitle">
              {data?.libraryName || '媒体播放'} {formatQuality(playbackInfo)}
            </div>
          </div>
        </div>
        <div className="player-toolbar-actions">
          {canSwitchMode && (
            <>
              <Segmented
                className="player-mode-switch"
                value={playbackPreference}
                onChange={(value) => handleModeChange(value as PlaybackModePreference)}
                options={[
                  { label: '自动', value: 'auto' },
                  { label: '直连', value: 'direct' },
                  { label: 'HLS', value: 'hls' },
                ]}
              />
              <Select
                className="player-quality-select"
                size="small"
                value={selectedQuality}
                disabled={playbackPreference === 'direct'}
                options={qualityOptions.map((option) => ({
                  value: option.value,
                  label: qualityLabel(option.value),
                }))}
                onChange={(value) => handleQualityChange(value as PlaybackQuality)}
              />
              <Segmented
                className="player-mode-switch player-transcode-switch"
                size="small"
                disabled={playbackPreference === 'direct'}
                value={selectedTranscodeMode}
                onChange={(value) => handleTranscodeModeChange(value as TranscodeMode)}
                options={transcodeModeOptions.map((option) => ({
                  value: option.value,
                  label: transcodeModeLabel(option.value),
                }))}
              />
            </>
          )}
          {renderTranscodeStatus()}
        </div>
      </div>
      {renderStage()}
    </div>
  );
};

export default PlayerPage;
