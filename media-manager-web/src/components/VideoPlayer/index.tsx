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
  trackCount: number,
  activeSubtitleIndex: number | null | undefined,
) {
  if (!video?.textTracks || trackCount <= 0) {
    return;
  }
  for (let i = 0; i < video.textTracks.length; i += 1) {
    if (activeSubtitleIndex == null) {
      video.textTracks[i].mode = 'disabled';
    } else {
      video.textTracks[i].mode = i === activeSubtitleIndex ? 'showing' : 'disabled';
    }
  }
}

type PlayerWithControls = Player & {
  paused?: boolean;
  getFullscreen?: () => boolean;
  exitFullscreen?: () => void;
  fullscreen?: () => void;
  play?: () => Promise<void> | void;
  pause?: () => void;
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
  onSeekRequest,
  onProgress,
  onError,
  onBufferingChange,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<Player | null>(null);
  const onProgressRef = useRef(onProgress);
  const onErrorRef = useRef(onError);
  const onBufferingChangeRef = useRef(onBufferingChange);
  const onSeekRequestRef = useRef(onSeekRequest);
  const mediaStartOffsetRef = useRef(mediaStartOffset);
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
    let onSeeking: (() => void) | null = null;
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
        plugins: mode === 'hls' ? [HlsPlugin] : [],
        hls: mode === 'hls' ? { retryCount: 3 } : undefined,
      });
      playerRef.current = player;

      const tryAutoplay = () => {
        if (!effectiveAutoplay || !player) return;
        const p = player as PlayerWithControls;
        Promise.resolve(p.play?.()).catch(() => {});
      };

      const attachSubtitles = () => {
        const video = containerRef.current?.querySelector('video');
        if (!video) return;
        video.querySelectorAll('track[data-mediamanager-subtitle="true"]').forEach((track) => track.remove());
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
        const defaultIndex = subtitles.findIndex((track) => track.defaultTrack);
        const resolvedIndex =
          activeSubtitleIndex != null
            ? activeSubtitleIndex
            : defaultIndex >= 0
              ? defaultIndex
              : subtitles.length > 0
                ? 0
                : null;
        applySubtitleSelection(video, subtitles.length, resolvedIndex);
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

      if (mode === 'hls' && onSeekRequestRef.current) {
        onSeeking = () => {
          const seekHandler = onSeekRequestRef.current;
          if (!seekHandler || !player) return;
          const targetAbsolute = mediaStartOffsetRef.current + (player.currentTime || 0);
          if (Math.abs(targetAbsolute - lastAbsoluteTimeRef.current) > 2) {
            lastAbsoluteTimeRef.current = targetAbsolute;
            seekHandler(Math.max(0, Math.floor(targetAbsolute)));
          }
        };
        player.on('seeking', onSeeking);
      }

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
            const target = Math.max(0, mediaStartOffsetRef.current + (p.currentTime || 0) - 10);
            onSeekRequestRef.current(target);
          } else {
            p.currentTime = Math.max(0, (p.currentTime || 0) - 10);
          }
        } else if (ev.code === 'ArrowRight') {
          ev.preventDefault();
          if (mode === 'hls' && onSeekRequestRef.current) {
            const target = mediaStartOffsetRef.current + (p.currentTime || 0) + 10;
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
        if (onSeeking) {
          player.off('seeking', onSeeking);
        }
        player.destroy();
      }
      playerRef.current = null;
    };
  }, [src, mode, poster, effectiveAutoplay, fill, subtitles, activeSubtitleIndex, startTime, mediaStartOffset, onSeekRequest]);

  useEffect(() => {
    const video = containerRef.current?.querySelector('video');
    applySubtitleSelection(video, subtitles.length, activeSubtitleIndex);
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
