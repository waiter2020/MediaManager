const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const REMEMBER_LOGIN_KEY = 'rememberLogin';
const REMEMBERED_USERNAME_KEY = 'rememberedUsername';

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

export interface SetSessionTokensOptions {
  remember?: boolean;
}

function readToken(storage: Storage, key: string): string | null {
  return storage.getItem(key);
}

function writeToken(storage: Storage, key: string, value: string | undefined) {
  if (value) {
    storage.setItem(key, value);
  } else {
    storage.removeItem(key);
  }
}

function clearTokensInStorage(storage: Storage) {
  storage.removeItem(ACCESS_TOKEN_KEY);
  storage.removeItem(REFRESH_TOKEN_KEY);
}

export function isRememberLogin(): boolean {
  const value = localStorage.getItem(REMEMBER_LOGIN_KEY);
  if (value === 'false') {
    return false;
  }
  return true;
}

export function getRememberedUsername(): string | null {
  return localStorage.getItem(REMEMBERED_USERNAME_KEY);
}

export function setRememberedUsername(username: string | null) {
  if (username) {
    localStorage.setItem(REMEMBERED_USERNAME_KEY, username);
  } else {
    localStorage.removeItem(REMEMBERED_USERNAME_KEY);
  }
}

export function getAccessToken() {
  return (
    readToken(localStorage, ACCESS_TOKEN_KEY) ?? readToken(sessionStorage, ACCESS_TOKEN_KEY)
  );
}

export function getRefreshToken() {
  return (
    readToken(localStorage, REFRESH_TOKEN_KEY) ?? readToken(sessionStorage, REFRESH_TOKEN_KEY)
  );
}

export function setSessionTokens(tokens: TokenPair, options?: SetSessionTokensOptions) {
  const remember = options?.remember ?? isRememberLogin();
  const targetStorage = remember ? localStorage : sessionStorage;
  const otherStorage = remember ? sessionStorage : localStorage;

  writeToken(targetStorage, ACCESS_TOKEN_KEY, tokens.accessToken);
  writeToken(targetStorage, REFRESH_TOKEN_KEY, tokens.refreshToken);
  clearTokensInStorage(otherStorage);
  localStorage.setItem(REMEMBER_LOGIN_KEY, remember ? 'true' : 'false');
}

export function clearSessionTokens() {
  clearTokensInStorage(localStorage);
  clearTokensInStorage(sessionStorage);
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
    setSessionTokens(data.data, { remember: isRememberLogin() });
    return data.data;
  }
  return null;
}
