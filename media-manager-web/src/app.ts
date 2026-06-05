import { RunTimeLayoutConfig } from '@umijs/max';
import { message } from 'antd';
import React from 'react';
import ScanProgressBanner from '@/components/ScanProgressBanner';
import ScrapeProgressBanner from '@/components/ScrapeProgressBanner';
import LibraryAccessBanner from '@/components/LibraryAccessBanner';
import { fetchAndApplyGlobalTheme, resolveEffectiveTheme, type ThemePreference } from '@/utils/theme';
import {
  clearSessionTokens,
  fetchCurrentUser,
  fetchSetupStatus,
  getAccessToken,
  getRefreshToken,
  postLogout,
  redirectToLogin,
  redirectToSetup,
  refreshSession,
} from '@/utils/authSession';

type RequestOptions = {
  url?: string;
  method?: string;
  headers?: Record<string, string>;
  data?: unknown;
  params?: Record<string, any>;
};

export const layout: RunTimeLayoutConfig = ({ initialState }) => {
  const themePref = (initialState?.theme as ThemePreference | undefined) ?? 'dark';
  const effective = resolveEffectiveTheme(themePref);

  return {
    childrenRender: (children) =>
      React.createElement(
        React.Fragment,
        null,
        React.createElement(ScanProgressBanner),
        React.createElement(ScrapeProgressBanner),
        React.createElement(LibraryAccessBanner),
        children,
      ),
    navTheme: effective === 'light' ? 'light' : 'realDark',
    logo: false,
    title: 'MediaManager',
    menu: {
      locale: false,
    },
    layout: 'side',
    breakpoint: 'lg',
    fixSiderbar: true,
    siderWidth: 220,
    contentWidth: 'Fluid',
    token: {
      sider: {
        colorMenuBackground: 'transparent',
        colorTextMenu: 'rgba(255,255,255,0.65)',
        colorTextMenuSelected: '#fff',
        colorBgMenuItemSelected: 'rgba(47,125,246,0.18)',
        colorTextMenuItemHover: '#fff',
        colorBgMenuItemHover: 'rgba(255,255,255,0.06)',
      },
      header: {
        colorBgHeader: 'rgba(8,9,16,0.74)',
      },
      colorPrimary: '#2f7df6',
    },
    menuFooterRender: () =>
      React.createElement(
        'a',
        {
          href: '/settings/profile',
          style: { color: 'rgba(255,255,255,0.65)', padding: '8px 16px', display: 'block' },
        },
        '个人设置',
      ),
    logout: async () => {
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        try {
          await postLogout(refreshToken);
        } catch {
          // The client should still clear local state even when server-side logout fails.
        }
      }
      clearSessionTokens();
      window.location.href = '/login';
    },
  };
};

export const request = {
  timeout: 30000,
  errorConfig: {
    errorHandler(error: { response?: Response }) {
      const { response } = error || {};
      if (response?.status === 401) {
        clearSessionTokens();
        redirectToLogin();
      } else if (response?.status === 403) {
        message.error('没有权限执行此操作');
      } else if (response?.status && response.status >= 500) {
        message.error('服务器错误，请稍后重试');
      }
    },
  },
  requestInterceptors: [
    (url: string, options: RequestOptions) => {
      const token = getAccessToken();
      const headers = { ...options.headers };
      if (token) {
        headers.Authorization = `Bearer ${token}`;
      }

      let params = options.params;
      if (params && typeof params === 'object') {
        const newParams = { ...params };
        Object.keys(newParams).forEach((key) => {
          if (Array.isArray(newParams[key])) {
            newParams[key] = newParams[key].join(',');
          }
        });
        params = newParams;
      }

      return {
        url,
        options: {
          ...options,
          headers,
          params,
        },
      };
    },
  ],
  responseInterceptors: [
    async (response: Response, options: RequestOptions) => {
      if (response.status !== 401) {
        return response;
      }
      if (window.location.pathname === '/login' || window.location.pathname === '/setup') {
        return response;
      }

      try {
        const tokens = await refreshSession();
        if (tokens?.accessToken) {
          const retryUrl = options?.url ?? response.url;
          const retryHeaders: Record<string, string> = {
            ...(options?.headers || {}),
            Authorization: `Bearer ${tokens.accessToken}`,
          };
          const retryInit: RequestInit = {
            method: options?.method || 'GET',
            headers: retryHeaders,
          };
          if (options?.data != null && options.method !== 'GET') {
            retryInit.body =
              typeof options.data === 'string' ? options.data : JSON.stringify(options.data);
            if (!retryHeaders['Content-Type']) {
              retryHeaders['Content-Type'] = 'application/json';
            }
          }
          return fetch(retryUrl, retryInit);
        }
      } catch {
        // Refresh failed. Fall through to local logout.
      }

      clearSessionTokens();
      redirectToLogin();
      return response;
    },
  ],
};

function mapCurrentUser(data: API.CurrentUser | undefined): API.CurrentUser | undefined {
  if (!data) return undefined;
  return {
    id: data.id,
    username: data.username,
    displayName: data.displayName,
    avatarPath: data.avatarPath,
    permissions: data.permissions ?? [],
    roles: data.roles,
  };
}

export async function getInitialState(): Promise<{
  isLogin: boolean;
  setupCompleted: boolean;
  currentUser?: API.CurrentUser;
  theme?: ThemePreference;
}> {
  const theme = await fetchAndApplyGlobalTheme();
  const token = getAccessToken();

  if (!token) {
    try {
      const statusRes = await fetchSetupStatus();
      if (!statusRes.data?.setupCompleted) {
        redirectToSetup();
        return { isLogin: false, setupCompleted: false, theme };
      }
    } catch {
      try {
        const res = await fetch('/api/v1/system/status');
        const data = await res.json();
        if (!data.data?.setupCompleted) {
          redirectToSetup();
          return { isLogin: false, setupCompleted: false, theme };
        }
      } catch {
        // Keep startup resilient while the API is booting.
      }
    }

    redirectToLogin();
    return { isLogin: false, setupCompleted: true, theme };
  }

  try {
    const meRes = await fetchCurrentUser();
    if (meRes.code === 200 && meRes.data) {
      return {
        isLogin: true,
        setupCompleted: true,
        currentUser: mapCurrentUser(meRes.data),
        theme,
      };
    }
  } catch {
    // Fall through to local logout.
  }

  clearSessionTokens();
  redirectToLogin();
  return { isLogin: false, setupCompleted: true, theme };
}
