export function playVideoPreviewFromRandomPosition(video: HTMLVideoElement): Promise<void> {
  const duration = video.duration;

  if (Number.isFinite(duration) && duration > 1) {
    const endGuard = Math.min(60, Math.max(5, duration * 0.15));
    const latestStart = Math.max(0, duration - endGuard);
    const earliestStart =
      duration > 60 ? Math.min(latestStart, Math.min(30, duration * 0.08)) : 0;
    const target = earliestStart + Math.random() * Math.max(0, latestStart - earliestStart);

    try {
      video.currentTime = target;
    } catch {
      // The browser may briefly reject seeking while the media timeline initializes.
    }
  }

  return video.play();
}
