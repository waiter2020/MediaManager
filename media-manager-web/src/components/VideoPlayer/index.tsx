import React, { useEffect, useRef } from 'react';
import Player from 'xgplayer';
import HlsPlugin from 'xgplayer-hls';

export interface VideoPlayerProps {
  src: string;
  mode: 'direct' | 'hls';
  poster?: string;
  autoplay?: boolean;
}

function appendToken(url: string): string {
  const token = localStorage.getItem('accessToken');
  if (!token || url.includes('token=')) {
    return url;
  }
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}token=${encodeURIComponent(token)}`;
}

const VideoPlayer: React.FC<VideoPlayerProps> = ({ src, mode, poster, autoplay = true }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<Player | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return undefined;
    }
    const fullUrl = src.startsWith('http') ? appendToken(src) : appendToken(`${window.location.origin}${src}`);

    const plugins = mode === 'hls' ? [HlsPlugin] : [];
    playerRef.current = new Player({
      el: containerRef.current,
      url: fullUrl,
      poster,
      autoplay,
      fluid: true,
      lang: 'zh-cn',
      plugins,
      hls: mode === 'hls' ? { retryCount: 3 } : undefined,
    });

    return () => {
      playerRef.current?.destroy();
      playerRef.current = null;
    };
  }, [src, mode, poster, autoplay]);

  return <div ref={containerRef} style={{ width: '100%', minHeight: 360 }} />;
};

export default VideoPlayer;
