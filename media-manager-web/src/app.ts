import { RunTimeLayoutConfig, useModel } from '@umijs/max';
import { message } from 'antd';
import React, { useEffect } from 'react';

export const layout: RunTimeLayoutConfig = () => {
  return {
    navTheme: 'realDark',
    logo: false,
    title: 'MediaManager',
    menu: {
      locale: false,
    },
    layout: 'side',
    fixSiderbar: true,
    siderWidth: 220,
    contentWidth: 'Fluid',
    token: {
      sider: {
        colorMenuBackground: 'transparent',
        colorTextMenu: 'rgba(255,255,255,0.65)',
        colorTextMenuSelected: '#fff',
        colorBgMenuItemSelected: 'rgba(22,104,220,0.15)',
        colorTextMenuItemHover: '#fff',
        colorBgMenuItemHover: 'rgba(255,255,255,0.06)',
      },
      header: {
        colorBgHeader: 'rgba(10,10,15,0.85)',
      },
      colorPrimary: '#1668dc',
    },
    logout: () => {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    },
    childrenRender: (children: React.ReactNode) => {
      return React.createElement(SSEWrapper, null, children);
    },
  };
};

const SSEWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // @ts-ignore
  const { connectSse } = useModel('global');

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      return;
    }
    const clientId = 'client-' + Math.random().toString(36).substring(7);
    const sse = connectSse(clientId);
    return () => {
      if (sse) sse.close();
    };
  }, []);

  return React.createElement(React.Fragment, null, children);
};


export const request = {
  timeout: 30000,
  errorConfig: {
    errorHandler(error: any) {
      const { response } = error || {};
      if (response?.status === 401) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        if (window.location.pathname !== '/login' && window.location.pathname !== '/setup') {
          window.location.href = '/login';
        }
      } else if (response?.status === 403) {
        message.error('没有权限执行此操作');
      } else if (response?.status >= 500) {
        message.error('服务器错误，请稍后重试');
      }
    },
  },
  requestInterceptors: [
    (url: string, options: any) => {
      const token = localStorage.getItem('accessToken');
      if (token) {
        options.headers = {
          ...options.headers,
          Authorization: `Bearer ${token}`,
        };
      }
      return { url, options };
    },
  ],
  responseInterceptors: [
    async (response: any) => {
      if (response.status === 401 && window.location.pathname !== '/login' && window.location.pathname !== '/setup') {
        const refreshToken = localStorage.getItem('refreshToken');
        if (refreshToken) {
          try {
            const res = await fetch('/api/v1/auth/refresh', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ refreshToken }),
            });
            const data = await res.json();
            if (data.code === 200 && data.data?.accessToken) {
              localStorage.setItem('accessToken', data.data.accessToken);
              if (data.data.refreshToken) {
                localStorage.setItem('refreshToken', data.data.refreshToken);
              }
              return response;
            }
          } catch (e) {
            // refresh failed
          }
        }
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
      }
      return response;
    },
  ],
};

export async function getInitialState() {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    try {
      const res = await fetch('/api/v1/system/status');
      const data = await res.json();
      if (!data.data?.setupCompleted) {
        if (window.location.pathname !== '/setup') {
          window.location.href = '/setup';
        }
        return { isLogin: false, setupCompleted: false };
      }
    } catch (e) {
      // ignore network errors for initial state
    }
    if (window.location.pathname !== '/login' && window.location.pathname !== '/setup') {
      window.location.href = '/login';
    }
    return { isLogin: false, setupCompleted: true };
  }
  return { isLogin: true, setupCompleted: true };
}
