const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

export interface ApiEnvelope<T = unknown> {
  code?: number;
  message?: string;
  data?: T;
}

export interface SessionUser {
  id?: number;
  username?: string;
  displayName?: string;
  avatarPath?: string;
  permissions?: string[];
  roles?: { code: string; name?: string }[];
}

export interface TokenPair {
  accessToken?: string;
  refreshToken?: string;
}

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setSessionTokens(tokens: TokenPair) {
  if (tokens.accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  }
  if (tokens.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }
}

export function clearSessionTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function authHeaders(): HeadersInit {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function redirectToLogin() {
  if (window.location.pathname !== '/login' && window.location.pathname !== '/setup') {
    window.location.href = '/login';
  }
}

export function redirectToSetup() {
  if (window.location.pathname !== '/setup') {
    window.location.href = '/setup';
  }
}

export async function fetchJson<T>(url: string, init?: RequestInit): Promise<ApiEnvelope<T>> {
  const res = await fetch(url, init);
  return res.json();
}

export async function fetchSetupStatus() {
  return fetchJson<{ setupCompleted?: boolean }>('/api/v1/auth/setup/status');
}

export async function fetchCurrentUser() {
  return fetchJson<SessionUser>('/api/v1/users/me', {
    headers: authHeaders(),
  });
}

export async function postLogout(refreshToken: string) {
  await fetch(`/api/v1/auth/logout?refreshToken=${encodeURIComponent(refreshToken)}`, {
    method: 'POST',
  });
}

export async function refreshSession() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  const data = await fetchJson<TokenPair>('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });

  if (data.code === 200 && data.data?.accessToken) {
    setSessionTokens(data.data);
    return data.data;
  }
  return null;
}
