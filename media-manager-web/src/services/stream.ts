export function getFileStreamUrl(fileId: number) {
  const token = localStorage.getItem('accessToken') || '';
  return `/api/v1/stream/${fileId}?token=${token}`;
}

export function getRawImageUrl(fileId: number) {
  const token = localStorage.getItem('accessToken') || '';
  return `/api/v1/stream/raw/${fileId}?token=${token}`;
}
