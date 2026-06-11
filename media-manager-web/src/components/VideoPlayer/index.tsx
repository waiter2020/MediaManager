import React, { useEffect, useRef } from 'react';
import Player from 'xgplayer';
import HlsPlugin from 'xgplayer-hls';
import 'xgplayer/dist/index.min.css';
import { getAccessToken } from '@/utils/authSession';
import { useIsMobileAutoplayDisabled } from '@/utils/useIsMobileAutoplayDisabled';
import { refreshStreamToken } from '@/services/stream';

const HLS_ERROR_REPORT_DELAY_MS = 4000;

export interface VideoPlayerProps {
  src: string;
  mode: 'direct' | 'hls';
  poster?: string;
  autoplay?: boolean;
  fill?: boolean;
  subtitles?: VideoSubtitleTrack[];
  activeSubtitleIndex?: number | null;
  startTime?: number;
  mediaStartOffset?: number;
  durationSeconds?: number;
  onSeekRequest?: (absoluteSeconds: number) => void;
  onProgress?: (seconds: number) => void;
  onError?: (message: string) => void;
  onBufferingChange?: (buffering: boolean) => void;
}

export interface VideoSubtitleTrack {
  id?: number;
  src: string;
  label?: string;
  language?: string;
  defaultTrack?: boolean;
}

function applySubtitleSelection(
  video: HTMLVideoElement | null,
  managedTracks: TextTrack[],
  activeSubtitleIndex: number | null | undefined,
) {
  if (!video?.textTracks || managedTracks.length <= 0) {
    return;
  }
  managedTracks.forEach((track, index) => {
    if (activeSubtitleIndex == null) {
      track.mode = 'disabled';
    } else {
      track.mode = index === activeSubtitleIndex ? 'showing' : 'disabled';
    }
  });
}

type PlayerWithControls = Player & {
  paused?: boolean;
  getFullscreen?: () => boolean;
  exitFullscreen?: () => void;
  fullscreen?: () => void;
  play?: () => Promise<void> | void;
  pause?: () => void;
};

type ProgressSeekData = {
  currentTime?: number;
  percent?: number;
};

type PlayerWithHooks = Player & {
  usePluginHooks?: (
    pluginName: string,
    hookName: string,
    handler: (plugin: unknown, event: unknown, data: ProgressSeekData) => boolean | void,
  ) => void;
};

function appendToken(url: string): string {
  const token = getAccessToken();
  if (!token || url.includes('token=')) {
    return url;
  }
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}token=${encodeURIComponent(token)}`;
}

function resolvePlaybackUrl(src: string) {
  return src.startsWith('http') ? appendToken(src) : appendToken(`${window.location.origin}${src}`);
}

const VideoPlayer: React.FC<VideoPlayerProps> = ({
  src,
  mode,
  poster,
  autoplay = true,
  fill = false,
  subtitles = [],
  activeSubtitleIndex,
  startTime = 0,
  mediaStartOffset = 0,
  durationSeconds,
  onSeekRequest,
  onProgress,
  onError,
  onBufferingChange,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<Player | null>(null);
  const managedTextTracksRef = useRef<TextTrack[]>([]);
  const onProgressRef = useRef(onProgress);
  const onErrorRef = useRef(onError);
  const onBufferingChangeRef = useRef(onBufferingChange);
  const onSeekRequestRef = useRef(onSeekRequest);
  const mediaStartOffsetRef = useRef(mediaStartOffset);
  const durationSecondsRef = useRef(durationSeconds);
  const lastAbsoluteTimeRef = useRef(mediaStartOffset);
  const autoplayDisabled = useIsMobileAutoplayDisabled();
  const effectiveAutoplay = autoplay && !autoplayDisabled;

  useEffect(() => {
    onProgressRef.current = onProgress;
  }, [onProgress]);

  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  useEffect(() => {
    onBufferingChangeRef.current = onBufferingChange;
  }, [onBufferingChange]);

  useEffect(() => {
    onSeekRequestRef.current = onSeekRequest;
  }, [onSeekRequest]);

  useEffect(() => {
    mediaStartOffsetRef.current = mediaStartOffset;
    lastAbsoluteTimeRef.current = mediaStartOffset;
  }, [mediaStartOffset]);

  useEffect(() => {
    durationSecondsRef.current = durationSeconds;
  }, [durationSeconds]);

  useEffect(() => {
    const silentRefresh = async () => {
      try {
        await refreshStreamToken();
      } catch (err) {
        console.error('[VideoPlayer] Failed to refresh stream token:', err);
      }
    };

    silentRefresh();
    const interval = setInterval(silentRefresh, 3 * 60 * 1000);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!containerRef.current) {
      return undefined;
    }

    let cancelled = false;
    let player: Player | null = null;
    let onKeyDown: ((ev: KeyboardEvent) => void) | null = null;
    let onTimeUpdate: (() => void) | null = null;
    let onPlayError: (() => void) | null = null;
    let errorReportTimer: ReturnType<typeof setTimeout> | null = null;
    let clearRecoveryHandlers: (() => void) | null = null;

    const setBuffering = (buffering: boolean) => {
      onBufferingChangeRef.current?.(buffering);
    };

    const clearPendingErrorReport = () => {
      if (errorReportTimer != null) {
        clearTimeout(errorReportTimer);
        errorReportTimer = null;
      }
    };

    const scheduleErrorReport = (hint: string) => {
      clearPendingErrorReport();
      const delay = mode === 'hls' ? HLS_ERROR_REPORT_DELAY_MS : 0;
      errorReportTimer = setTimeout(() => {
        if (cancelled) return;
        onErrorRef.current?.(hint);
      }, delay);
    };

    const registerErrorRecoveryHandlers = () => {
      if (!player) return;
      const recoverFromTransientError = () => {
        clearPendingErrorReport();
        setBuffering(false);
      };
      player.on('canplay', recoverFromTransientError);
      player.on('playing', recoverFromTransientError);
      player.on('loadeddata', recoverFromTransientError);
      clearRecoveryHandlers = () => {
        player?.off('canplay', recoverFromTransientError);
        player?.off('playing', recoverFromTransientError);
        player?.off('loadeddata', recoverFromTransientError);
      };
    };

    const initPlayer = async () => {
      setBuffering(mode === 'hls');

      if (mode === 'hls') {
        try {
          await refreshStreamToken();
        } catch (err) {
          console.error('[VideoPlayer] Failed to refresh stream token before HLS playback:', err);
        }
      }

      if (cancelled || !containerRef.current) {
        setBuffering(false);
        return;
      }

      player = new Player({
        el: containerRef.current,
        url: resolvePlaybackUrl(src),
        poster,
        autoplay: effectiveAutoplay,
        fluid: !fill,
        width: '100%',
        height: fill ? '100%' : undefined,
        lang: 'zh-cn',
        isLive: mode === 'hls' ? false : undefined,
        customDuration:
          mode === 'hls' && durationSeconds != null && durationSeconds > 0 ? durationSeconds : undefined,
        timeOffset: mode === 'hls' && mediaStartOffset > 0 ? mediaStartOffset : undefined,
        plugins: mode === 'hls' ? [HlsPlugin] : [],
        hlsvod:
          mode === 'hls'
            ? {
                retryCount: 3,
                preloadTime: 30,
              }
            : undefined,
      });
      playerRef.current = player;

      const resolveProgressSeekTarget = (data: ProgressSeekData): number | null => {
        const totalDuration = durationSecondsRef.current;
        let targetAbsolute: number | null = null;
        if (typeof data.currentTime === 'number' && Number.isFinite(data.currentTime)) {
          targetAbsolute = Math.floor(data.currentTime);
        } else if (typeof data.percent === 'number' && totalDuration != null && totalDuration > 0) {
          targetAbsolute = Math.floor(data.percent * totalDuration);
        }
        if (targetAbsolute == null) {
          return null;
        }
        targetAbsolute = Math.max(0, targetAbsolute);
        if (totalDuration != null && totalDuration > 0) {
          targetAbsolute = Math.min(targetAbsolute, totalDuration);
        }
        return targetAbsolute;
      };

      const registerProgressSeekHooks = () => {
        if (mode !== 'hls' || !onSeekRequestRef.current) {
          return;
        }
        const hookedPlayer = player as PlayerWithHooks;
        if (typeof hookedPlayer.usePluginHooks !== 'function') {
          return;
        }
        const handleProgressSeek = (_plugin: unknown, _event: unknown, data: ProgressSeekData) => {
          const seekHandler = onSeekRequestRef.current;
          if (!seekHandler) {
            return false;
          }
          const targetAbsolute = resolveProgressSeekTarget(data);
          if (targetAbsolute == null) {
            return false;
          }
          if (Math.abs(targetAbsolute - lastAbsoluteTimeRef.current) < 1) {
            return false;
          }
          lastAbsoluteTimeRef.current = targetAbsolute;
          seekHandler(targetAbsolute);
          return false;
        };
        hookedPlayer.usePluginHooks('progress', 'click', handleProgressSeek);
        hookedPlayer.usePluginHooks('progress', 'dragend', handleProgressSeek);
      };
      registerProgressSeekHooks();

      const tryAutoplay = () => {
        if (!effectiveAutoplay || !player) return;
        const p = player as PlayerWithControls;
        Promise.resolve(p.play?.()).catch(() => {});
      };

      const attachSubtitles = () => {
        const video = containerRef.current?.querySelector('video');
        if (!video) return;
        video.querySelectorAll('track[data-mediamanager-subtitle="true"]').forEach((track) => track.remove());
        managedTextTracksRef.current = [];
        subtitles.forEach((subtitle, index) => {
          const track = document.createElement('track');
          track.kind = 'subtitles';
          track.src = resolvePlaybackUrl(subtitle.src);
          track.label = subtitle.label || subtitle.language || `Subtitle ${index + 1}`;
          track.srclang = subtitle.language || 'und';
          track.default = subtitle.defaultTrack || index === 0;
          track.dataset.mediamanagerSubtitle = 'true';
          video.appendChild(track);
        });
        managedTextTracksRef.current = Array.from(
          video.querySelectorAll('track[data-mediamanager-subtitle="true"]'),
        )
          .map((element) => (element as HTMLTrackElement).track)
          .filter((textTrack): textTrack is TextTrack => textTrack != null);
        const defaultIndex = subtitles.findIndex((track) => track.defaultTrack);
        const resolvedIndex =
          activeSubtitleIndex != null
            ? activeSubtitleIndex
            : defaultIndex >= 0
              ? defaultIndex
              : subtitles.length > 0
                ? 0
                : null;
        applySubtitleSelection(video, managedTextTracksRef.current, resolvedIndex);
      };
      attachSubtitles();
      player.once('loadedmetadata', attachSubtitles);

      if (startTime > 0 && mode !== 'hls') {
        player.once('loadedmetadata', () => {
          player!.currentTime = startTime;
        });
      }

      let lastReported = 0;
      onTimeUpdate = () => {
        const relative = player!.currentTime || 0;
        const absolute = mediaStartOffsetRef.current + relative;
        lastAbsoluteTimeRef.current = absolute;
        const t = Math.floor(absolute);
        if (t > 0 && Math.abs(t - lastReported) >= 15) {
          lastReported = t;
          onProgressRef.current?.(t);
        }
      };
      player.on('timeupdate', onTimeUpdate);

      player.on('waiting', () => setBuffering(true));
      player.on('canplay', () => {
        setBuffering(false);
        tryAutoplay();
      });
      player.once('ready', tryAutoplay);
      player.on('playing', () => setBuffering(false));

      registerErrorRecoveryHandlers();

      onPlayError = () => {
        const hint =
          mode === 'hls'
            ? 'HLS 播放失败：请确认 FFmpeg 可用，媒体文件路径可访问，且当前编码/质量档位可用。'
            : '播放失败：请确认文件存在，并且当前账号拥有播放权限。';
        if (mode === 'hls') {
          setBuffering(true);
        }
        scheduleErrorReport(hint);
      };
      player.on('error', onPlayError);

      onKeyDown = (ev: KeyboardEvent) => {
        const p = playerRef.current as PlayerWithControls | null;
        if (!p) return;
        if (ev.code === 'Space') {
          ev.preventDefault();
          if (p.paused) p.play?.();
          else p.pause?.();
        } else if (ev.code === 'ArrowLeft') {
          ev.preventDefault();
          if (mode === 'hls' && onSeekRequestRef.current) {
            const current = mediaStartOffsetRef.current + (p.currentTime || 0);
            const target = Math.max(0, Math.floor(current - 10));
            onSeekRequestRef.current(target);
          } else {
            p.currentTime = Math.max(0, (p.currentTime || 0) - 10);
          }
        } else if (ev.code === 'ArrowRight') {
          ev.preventDefault();
          if (mode === 'hls' && onSeekRequestRef.current) {
            const current = mediaStartOffsetRef.current + (p.currentTime || 0);
            const maxDuration = durationSecondsRef.current;
            const target =
              maxDuration != null && maxDuration > 0
                ? Math.min(maxDuration, Math.floor(current + 10))
                : Math.floor(current + 10);
            onSeekRequestRef.current(target);
          } else {
            p.currentTime = (p.currentTime || 0) + 10;
          }
        } else if (ev.key === 'f' || ev.key === 'F') {
          ev.preventDefault();
          if (typeof p.getFullscreen === 'function') {
            p.getFullscreen() ? p.exitFullscreen?.() : p.fullscreen?.();
          } else if (typeof p.fullscreen === 'function') {
            p.fullscreen();
          }
        }
      };
      window.addEventListener('keydown', onKeyDown);
    };

    initPlayer();

    return () => {
      cancelled = true;
      clearPendingErrorReport();
      clearRecoveryHandlers?.();
      setBuffering(false);
      if (onKeyDown) {
        window.removeEventListener('keydown', onKeyDown);
      }
      if (player) {
        if (onPlayError) {
          player.off('error', onPlayError);
        }
        if (onTimeUpdate) {
          player.off('timeupdate', onTimeUpdate);
        }
        player.destroy();
      }
      playerRef.current = null;
    };
  }, [src, mode, poster, effectiveAutoplay, fill, subtitles, activeSubtitleIndex, startTime, mediaStartOffset, durationSeconds, onSeekRequest]);

  useEffect(() => {
    const video = containerRef.current?.querySelector('video');
    applySubtitleSelection(video, managedTextTracksRef.current, activeSubtitleIndex);
  }, [activeSubtitleIndex, subtitles.length]);

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height: fill ? '100%' : undefined,
        minHeight: fill ? 0 : 360,
      }}
    />
  );
};

export default VideoPlayer;
