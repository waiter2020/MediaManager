import React, { useEffect, useRef } from 'react';
import Player from 'xgplayer';
import HlsPlugin from 'xgplayer-hls';
import 'xgplayer/dist/index.min.css';
import { getAccessToken } from '@/utils/authSession';
import { refreshStreamToken } from '@/services/stream';

export interface VideoPlayerProps {
  src: string;
  mode: 'direct' | 'hls';
  poster?: string;
  autoplay?: boolean;
  startTime?: number;
  onProgress?: (seconds: number) => void;
  onError?: (message: string) => void;
}

type PlayerWithControls = Player & {
  paused?: boolean;
  getFullscreen?: () => boolean;
  exitFullscreen?: () => void;
  fullscreen?: () => void;
  play?: () => void;
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
  startTime = 0,
  onProgress,
  onError,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<Player | null>(null);

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

    const player = new Player({
      el: containerRef.current,
      url: resolvePlaybackUrl(src),
      poster,
      autoplay,
      fluid: true,
      lang: 'zh-cn',
      plugins: mode === 'hls' ? [HlsPlugin] : [],
      hls: mode === 'hls' ? { retryCount: 3 } : undefined,
    });
    playerRef.current = player;

    if (startTime > 0) {
      player.once('loadedmetadata', () => {
        player.currentTime = startTime;
      });
    }

    let lastReported = 0;
    const onTimeUpdate = () => {
      const t = Math.floor(player.currentTime || 0);
      if (t > 0 && Math.abs(t - lastReported) >= 15) {
        lastReported = t;
        onProgress?.(t);
      }
    };
    if (onProgress) {
      player.on('timeupdate', onTimeUpdate);
    }

    const onPlayError = () => {
      const hint =
        mode === 'hls'
          ? 'HLS 播放失败：请确认 FFmpeg 可用，且媒体文件路径在容器内可访问。'
          : '播放失败：请确认文件存在，并且当前账号拥有播放权限。';
      onError?.(hint);
    };
    player.on('error', onPlayError);

    const onKeyDown = (ev: KeyboardEvent) => {
      const p = playerRef.current as PlayerWithControls | null;
      if (!p) return;
      if (ev.code === 'Space') {
        ev.preventDefault();
        if (p.paused) p.play?.();
        else p.pause?.();
      } else if (ev.code === 'ArrowLeft') {
        ev.preventDefault();
        p.currentTime = Math.max(0, (p.currentTime || 0) - 10);
      } else if (ev.code === 'ArrowRight') {
        ev.preventDefault();
        p.currentTime = (p.currentTime || 0) + 10;
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

    return () => {
      player.off('error', onPlayError);
      window.removeEventListener('keydown', onKeyDown);
      if (onProgress) {
        player.off('timeupdate', onTimeUpdate);
      }
      playerRef.current?.destroy();
      playerRef.current = null;
    };
  }, [src, mode, poster, autoplay, startTime, onProgress, onError]);

  return <div ref={containerRef} style={{ width: '100%', minHeight: 360 }} />;
};

export default VideoPlayer;
