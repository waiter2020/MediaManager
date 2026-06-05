export interface OpenPlayerWindowOptions {
  fileId?: number;
  position?: number;
}

export function buildPlayerUrl(mediaItemId: number, options: OpenPlayerWindowOptions = {}) {
  const url = new URL(`/player/${mediaItemId}`, window.location.origin);
  if (options.fileId != null) {
    url.searchParams.set('fileId', String(options.fileId));
  }
  if (options.position != null && options.position > 0) {
    url.searchParams.set('t', String(Math.floor(options.position)));
  }
  return url.toString();
}

export function openPlayerWindow(mediaItemId: number, options: OpenPlayerWindowOptions = {}) {
  const width = window.screen?.availWidth || window.innerWidth || 1280;
  const height = window.screen?.availHeight || window.innerHeight || 720;
  const left = window.screen?.availLeft || 0;
  const top = window.screen?.availTop || 0;
  const features = [
    'popup=yes',
    'toolbar=no',
    'location=no',
    'status=no',
    'menubar=no',
    'scrollbars=no',
    'resizable=yes',
    'fullscreen=yes',
    `left=${left}`,
    `top=${top}`,
    `width=${width}`,
    `height=${height}`,
  ].join(',');

  const opened = window.open(buildPlayerUrl(mediaItemId, options), '_blank', features);
  if (!opened) {
    return false;
  }

  try {
    opened.focus();
    opened.moveTo(left, top);
    opened.resizeTo(width, height);
  } catch {
    // Some browsers disallow resizing scripted windows; the requested size still helps.
  }

  return true;
}
