import { request } from '@umijs/max';

export async function getDiscover(limit = 20) {
  return request('/api/v1/discover', { method: 'GET', params: { limit } });
}
