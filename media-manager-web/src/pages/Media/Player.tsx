import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useParams, useSearchParams } from '@umijs/max';
import {
  ArrowLeftOutlined,
  CustomerServiceOutlined,
  PictureOutlined,
  SearchOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { Button, Result, Segmented, Select, Spin, Tooltip } from 'antd';
import SubtitleSearchModal from '@/components/SubtitleSearchModal';
import VideoPlayer from '@/components/VideoPlayer';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { getItemDetail } from '@/services/media';
import {
  appendAuthToken,
  getPlaybackInfo,
  getRawImageUrl,
  getSubtitleTrackUrl,
  getTranscodeSpeed,
  refreshStreamToken,
  stopTranscode,
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
import type { MediaItem, MediaSubtitle } from '@/types/media';
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

function formatPlaybackTime(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${minutes}:${String(secs).padStart(2, '0')}`;
}

const TRANSCODING_REASON_LABELS: Record<string, string> = {
  ContainerNotSupported: '容器不支持直连',
  VideoCodecNotSupported: '视频编码不支持直连',
  AudioCodecNotSupported: '音频编码不支持直连',
  PlaybackModeRequested: '已选择 HLS 模式',
  QualityRequested: '已选择转码质量',
  TranscodeModeRequested: '已选择编码方式',
};

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
  const [playerBuffering, setPlayerBuffering] = useState(false);
  const [switchingPlayback, setSwitchingPlayback] = useState(false);
  const [streamKey, setStreamKey] = useState(0);
  const [mediaStartOffset, setMediaStartOffset] = useState(0);
  const [transcodeSpeed, setTranscodeSpeed] = useState<TranscodeTelemetry | null>(null);
  const [activeSubtitleId, setActiveSubtitleId] = useState<number | 'off' | null>(null);
  const [subtitleDelay, setSubtitleDelay] = useState(0);
  const [subtitleSearchVisible, setSubtitleSearchVisible] = useState(false);
  const [seekTargetSeconds, setSeekTargetSeconds] = useState<number | null>(null);
  const lastAudioReportRef = useRef(0);
  const seekDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingSeekRef = useRef<number | null>(null);
  const lastSeekTargetRef = useRef<number | null>(null);
  const autoplayDisabled = useIsMobileAutoplayDisabled();

  const handleVideoProgress = useCallback(
    (seconds: number) => {
      if (data?.id) {
        recordPlay({ mediaItemId: data.id, position: seconds }).catch(() => {});
      }
    },
    [data?.id],
  );

  const numericId = Number(id);
  const startSec = Number(searchParams.get('t'));
  const startTime = Number.isFinite(startSec) && startSec > 0 ? startSec : 0;
  const searchKey = searchParams.toString();

  const handlePlayerError = useCallback(
    (message: string) => {
      if (playbackMode === 'hls') {
        console.warn('[Player] HLS playback error (may recover):', message);
      }
      setPlayError(message);
    },
    [playbackMode],
  );

  const handleBufferingChange = useCallback((buffering: boolean) => {
    setPlayerBuffering(buffering);
  }, []);

  useEffect(() => {
    return () => {
      if (seekDebounceRef.current) {
        clearTimeout(seekDebounceRef.current);
      }
    };
  }, []);

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
    resumeSeconds: number = startTime,
  ) => {
    try {
      await refreshStreamToken();
    } catch {
      // VideoPlayer also refreshes before HLS init; continue loading playback info.
    }

    const playback = await getPlaybackInfo(nextFileId, {
      mode: preferredMode,
      quality,
      transcodeMode,
      start: resumeSeconds > 0 ? resumeSeconds : undefined,
    });
    if (playback.code === 200 && playback.data?.url) {
      setPlaybackInfo(playback.data);
      setPlaybackMode(playback.data.mode === 'hls' ? 'hls' : 'direct');
      setStreamUrl(appendAuthToken(playback.data.url));
      setMediaStartOffset(playback.data.startOffset ?? (resumeSeconds > 0 ? resumeSeconds : 0));
      lastSeekTargetRef.current = playback.data.startOffset ?? (resumeSeconds > 0 ? resumeSeconds : 0);
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
  }, [numericId, searchKey, startTime]);

  const runPlaybackSwitch = async (action: () => Promise<void>) => {
    setSwitchingPlayback(true);
    setPlayError(null);
    try {
      await action();
    } finally {
      setSwitchingPlayback(false);
    }
  };

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

  const availableSubtitles = useMemo(() => {
    const subtitles = data?.subtitles || [];
    if (fileId == null) {
      return subtitles;
    }
    return subtitles.filter(
      (subtitle) => subtitle.mediaFileId == null || subtitle.mediaFileId === fileId,
    );
  }, [data?.subtitles, fileId]);

  const subtitleTracks = useMemo(
    () => {
      const offset = playbackMode === 'hls' ? mediaStartOffset : 0;
      return availableSubtitles.map((subtitle) => ({
        id: subtitle.id,
        src: getSubtitleTrackUrl(subtitle.id, { offset, delay: subtitleDelay }),
        label: subtitle.title || subtitle.language || subtitle.fileName || `Subtitle ${subtitle.id}`,
        language: subtitle.language,
        defaultTrack: subtitle.defaultTrack,
      }));
    },
    [availableSubtitles, playbackMode, mediaStartOffset, subtitleDelay],
  );

  const activeSubtitleIndex = useMemo(() => {
    if (activeSubtitleId === 'off') {
      return null;
    }
    if (activeSubtitleId != null) {
      const index = subtitleTracks.findIndex((track) => track.id === activeSubtitleId);
      if (index >= 0) {
        return index;
      }
    }
    const defaultIndex = subtitleTracks.findIndex((track) => track.defaultTrack);
    if (defaultIndex >= 0) {
      return defaultIndex;
    }
    return subtitleTracks.length > 0 ? 0 : null;
  }, [activeSubtitleId, subtitleTracks]);

  useEffect(() => {
    if (!availableSubtitles.length) {
      setActiveSubtitleId('off');
      return;
    }
    const currentExists =
      activeSubtitleId != null &&
      activeSubtitleId !== 'off' &&
      availableSubtitles.some((subtitle) => subtitle.id === activeSubtitleId);
    if (activeSubtitleId == null || (!currentExists && activeSubtitleId !== 'off')) {
      const defaultSubtitle =
        availableSubtitles.find((subtitle) => subtitle.defaultTrack) || availableSubtitles[0];
      setActiveSubtitleId(defaultSubtitle?.id ?? 'off');
    }
  }, [availableSubtitles, activeSubtitleId]);

  const refreshItemDetail = useCallback(async () => {
    const res = await getItemDetail(numericId);
    if (res.code === 200 && res.data) {
      setData(res.data);
    }
  }, [numericId]);

  const handleSubtitleDownloaded = useCallback((subtitle: MediaSubtitle) => {
    setData((current) =>
      current
        ? {
            ...current,
            subtitles: [...(current.subtitles || []).filter((item) => item.id !== subtitle.id), subtitle],
          }
        : current,
    );
    setActiveSubtitleId(subtitle.id);
    refreshItemDetail().catch(() => {});
  }, [refreshItemDetail]);

  const adjustSubtitleDelay = useCallback((delta: number) => {
    setSubtitleDelay((current) => {
      const next = Math.round((current + delta) * 2) / 2;
      return Math.max(-10, Math.min(10, next));
    });
  }, []);

  const subtitleDelayLabel = useMemo(() => {
    if (Math.abs(subtitleDelay) < 0.001) {
      return '0s';
    }
    const sign = subtitleDelay > 0 ? '+' : '';
    return `${sign}${subtitleDelay.toFixed(1)}s`;
  }, [subtitleDelay]);

  const subtitleSelectOptions = useMemo(
    () => [
      { value: 'off', label: '关闭字幕' },
      ...availableSubtitles.map((subtitle) => ({
        value: subtitle.id,
        label: `${subtitle.title || subtitle.language || subtitle.fileName || `Subtitle ${subtitle.id}`}${
          subtitle.source ? ` (${subtitle.source})` : ''
        }`,
      })),
    ],
    [availableSubtitles],
  );

  const handleModeChange = async (mode: PlaybackModePreference) => {
    if (fileId == null || data?.type === 'IMAGE') return;
    setPlaybackPreference(mode);
    if (mode === 'direct') {
      setPlaybackMode('direct');
    }
    await runPlaybackSwitch(async () => {
      try {
        await loadPlayback(fileId, mode);
      } catch {
        setPlayError(mode === 'hls' ? '无法启动兼容转码播放。' : '无法启动原画直连播放。');
      }
    });
  };

  const handleQualityChange = async (quality: PlaybackQuality) => {
    setSelectedQuality(quality);
    if (fileId == null || data?.type === 'IMAGE') return;
    await runPlaybackSwitch(async () => {
      try {
        await loadPlayback(fileId, playbackPreference, quality, selectedTranscodeMode);
      } catch {
        setPlayError('无法切换到所选质量档位。');
      }
    });
  };

  const handleTranscodeModeChange = async (mode: TranscodeMode) => {
    setSelectedTranscodeMode(mode);
    if (fileId == null || data?.type === 'IMAGE') return;
    await runPlaybackSwitch(async () => {
      try {
        await loadPlayback(fileId, playbackPreference, selectedQuality, mode);
      } catch {
        setPlayError('无法切换到所选编码方式。');
      }
    });
  };

  const executeSeek = useCallback(
    async (absoluteSeconds: number) => {
      if (fileId == null || playbackMode !== 'hls') return;
      const floored = Math.max(0, Math.floor(absoluteSeconds));
      lastSeekTargetRef.current = floored;
      await runPlaybackSwitch(async () => {
        setStreamKey((k) => k + 1);
        await loadPlayback(
          fileId,
          playbackPreference,
          selectedQuality,
          selectedTranscodeMode,
          floored,
        );
      });
      setSeekTargetSeconds(null);
    },
    [fileId, playbackMode, playbackPreference, selectedQuality, selectedTranscodeMode],
  );

  const handleSeekRequest = useCallback(
    (absoluteSeconds: number) => {
      if (fileId == null || switchingPlayback || playbackMode !== 'hls') return;
      const floored = Math.max(0, Math.floor(absoluteSeconds));
      setSeekTargetSeconds(floored);
      pendingSeekRef.current = floored;
      if (seekDebounceRef.current) {
        clearTimeout(seekDebounceRef.current);
      }
      seekDebounceRef.current = setTimeout(() => {
        const target = pendingSeekRef.current;
        pendingSeekRef.current = null;
        if (target == null) {
          return;
        }
        if (lastSeekTargetRef.current === target && !switchingPlayback) {
          setSeekTargetSeconds(null);
          return;
        }
        executeSeek(target);
      }, 400);
    },
    [fileId, switchingPlayback, playbackMode, executeSeek],
  );

  useEffect(() => {
    return () => {
      if (fileId != null && playbackMode === 'hls') {
        stopTranscode(fileId).catch(() => {});
      }
    };
  }, [fileId, playbackMode]);

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

    if (switchingPlayback) {
      const tip =
        seekTargetSeconds != null
          ? `正在跳转到 ${formatPlaybackTime(seekTargetSeconds)}，等待转码…`
          : '正在切换播放参数...';
      return (
        <div className="player-center">
          <Spin size="large" tip={tip} />
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
        {playerBuffering && playbackMode === 'hls' ? (
          <div className="player-hls-buffering-hint">正在等待首段转码，请稍候…</div>
        ) : null}
        <VideoPlayer
          key={streamKey}
          src={streamUrl}
          mode={playbackMode}
          poster={poster}
          fill
          startTime={playbackMode === 'hls' ? 0 : startTime}
          mediaStartOffset={mediaStartOffset}
          durationSeconds={playbackInfo?.durationSeconds}
          onSeekRequest={playbackMode === 'hls' ? handleSeekRequest : undefined}
          subtitles={subtitleTracks}
          activeSubtitleIndex={activeSubtitleIndex}
          onError={handlePlayerError}
          onBufferingChange={handleBufferingChange}
          onProgress={handleVideoProgress}
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
              {playbackInfo?.transcodingReasons?.length ? (
                <span className="player-transcode-reasons">
                  {' '}
                  ·{' '}
                  {playbackInfo.transcodingReasons
                    .map((reason) => TRANSCODING_REASON_LABELS[reason] || reason)
                    .join(' · ')}
                </span>
              ) : null}
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
                  {
                    label: '直连',
                    value: 'direct',
                    disabled: playbackInfo?.directPlayable === false,
                  },
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
                  label:
                    option.value === 'hardware' ? (
                      <Tooltip title="需在 设置 → 媒体处理 中配置 GPU 加速类型">
                        {transcodeModeLabel(option.value)}
                      </Tooltip>
                    ) : (
                      transcodeModeLabel(option.value)
                    ),
                }))}
              />
            </>
          )}
          {data?.type !== 'IMAGE' && data?.type !== 'AUDIO' && (
            <>
              <Select
                className="player-subtitle-select"
                size="small"
                value={activeSubtitleId === 'off' ? 'off' : activeSubtitleId ?? 'off'}
                options={subtitleSelectOptions}
                onChange={(value) => setActiveSubtitleId(value === 'off' ? 'off' : Number(value))}
              />
              <div className="player-subtitle-delay">
                <Button
                  className="player-icon-button"
                  size="small"
                  aria-label="字幕延迟减 0.5 秒"
                  disabled={activeSubtitleId === 'off' || subtitleDelay <= -10}
                  onClick={() => adjustSubtitleDelay(-0.5)}
                >
                  −
                </Button>
                <span className="player-subtitle-delay-value" title="字幕延迟">{subtitleDelayLabel}</span>
                <Button
                  className="player-icon-button"
                  size="small"
                  aria-label="字幕延迟加 0.5 秒"
                  disabled={activeSubtitleId === 'off' || subtitleDelay >= 10}
                  onClick={() => adjustSubtitleDelay(0.5)}
                >
                  +
                </Button>
              </div>
              <Button
                className="player-icon-button"
                icon={<SearchOutlined />}
                aria-label="搜索字幕"
                onClick={() => setSubtitleSearchVisible(true)}
              />
            </>
          )}
          {renderTranscodeStatus()}
        </div>
      </div>
      {renderStage()}
      {data?.id ? (
        <SubtitleSearchModal
          itemId={data.id}
          defaultQuery={data.title}
          fileId={fileId}
          open={subtitleSearchVisible}
          onClose={() => setSubtitleSearchVisible(false)}
          onDownloaded={handleSubtitleDownloaded}
        />
      ) : null}
    </div>
  );
};

export default PlayerPage;
