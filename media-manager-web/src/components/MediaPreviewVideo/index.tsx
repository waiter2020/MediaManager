import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getPreviewPlaybackInfo } from '@/services/stream';
import {
  listOrderedOriginsForFile,
  resolveMediaPlaybackUrl,
} from '@/utils/streamOrigin';
import { playVideoPreviewFromRandomPosition } from '@/utils/videoPreview';

export interface MediaPreviewVideoProps {
  fileId: number;
  active: boolean;
  className?: string;
  startSeconds?: number;
  loopEndSeconds?: number;
  randomStart?: boolean;
  onError?: () => void;
}

const MediaPreviewVideo: React.FC<MediaPreviewVideoProps> = ({
  fileId,
  active,
  className,
  startSeconds,
  loopEndSeconds,
  randomStart = true,
  onError,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playbackUrl, setPlaybackUrl] = useState<string | null>(null);
  const [originAttemptIndex, setOriginAttemptIndex] = useState(0);
  const requestIdRef = useRef(0);
  const originsExhaustedRef = useRef(false);

  const releasePlayback = (video: HTMLVideoElement | null) => {
    if (!video) {
      return;
    }
    video.pause();
    video.removeAttribute('src');
    video.load();
  };

  const tryNextOrigin = useCallback(() => {
    const maxAttempts = listOrderedOriginsForFile(fileId).length;
    setOriginAttemptIndex((current) => {
      if (current + 1 < maxAttempts) {
        return current + 1;
      }
      if (!originsExhaustedRef.current) {
        originsExhaustedRef.current = true;
        onError?.();
      }
      return current;
    });
  }, [fileId, onError]);

  useEffect(() => {
    originsExhaustedRef.current = false;
    setOriginAttemptIndex(0);
  }, [active, fileId, playbackUrl]);

  useEffect(() => {
    if (!active) {
      requestIdRef.current += 1;
      setPlaybackUrl(null);
      releasePlayback(videoRef.current);
      return undefined;
    }

    const requestId = ++requestIdRef.current;
    let cancelled = false;

    const loadPreview = async () => {
      try {
        const playback = await getPreviewPlaybackInfo(fileId, { kickoff: false });
        if (cancelled || requestId !== requestIdRef.current) {
          return;
        }
        if (playback.code !== 200 || !playback.data?.url) {
          onError?.();
          return;
        }
        if (playback.data.directPlayable === false) {
          onError?.();
          return;
        }
        setPlaybackUrl(playback.data.url);
      } catch {
        if (!cancelled && requestId === requestIdRef.current) {
          onError?.();
        }
      }
    };

    void loadPreview();

    return () => {
      cancelled = true;
      releasePlayback(videoRef.current);
    };
  }, [active, fileId, onError]);

  useEffect(() => {
    const video = videoRef.current;
    if (!active || !video || !playbackUrl) {
      return undefined;
    }

    releasePlayback(video);

    video.src = resolveMediaPlaybackUrl(playbackUrl, fileId, originAttemptIndex);
    const handleLoadedMetadata = () => {
      if (startSeconds != null && startSeconds >= 0) {
        try {
          video.currentTime = startSeconds;
        } catch {
          // Browser may reject seeking before the timeline is ready.
        }
        video.play().catch(() => tryNextOrigin());
        return;
      }
      if (randomStart) {
        playVideoPreviewFromRandomPosition(video).catch(() => tryNextOrigin());
        return;
      }
      video.play().catch(() => tryNextOrigin());
    };
    video.addEventListener('loadedmetadata', handleLoadedMetadata, { once: true });

    return () => {
      releasePlayback(video);
    };
  }, [active, fileId, originAttemptIndex, playbackUrl, randomStart, startSeconds, tryNextOrigin]);

  useEffect(
    () => () => {
      releasePlayback(videoRef.current);
    },
    [fileId],
  );

  if (!active || !playbackUrl) {
    return null;
  }

  return (
    <video
      ref={videoRef}
      className={className}
      muted
      playsInline
      preload="metadata"
      onTimeUpdate={(event) => {
        if (startSeconds == null || loopEndSeconds == null) {
          return;
        }
        if (event.currentTarget.currentTime >= loopEndSeconds) {
          event.currentTarget.currentTime = Math.max(0, startSeconds);
          event.currentTarget.play().catch(() => tryNextOrigin());
        }
      }}
      onEnded={(event) => {
        if (startSeconds != null && startSeconds >= 0) {
          event.currentTarget.currentTime = Math.max(0, startSeconds);
          event.currentTarget.play().catch(() => tryNextOrigin());
          return;
        }
        if (randomStart) {
          playVideoPreviewFromRandomPosition(event.currentTarget).catch(() => tryNextOrigin());
          return;
        }
        event.currentTarget.play().catch(() => tryNextOrigin());
      }}
      onError={() => tryNextOrigin()}
    />
  );
};

export default MediaPreviewVideo;
