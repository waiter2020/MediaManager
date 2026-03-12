import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {
    configProvider: {},
    dark: true,
    theme: {
      token: {
        colorPrimary: '#1668dc',
        colorBgBase: '#0a0a0f',
        colorBgContainer: '#141420',
        colorBgElevated: '#1c1c2e',
        colorBgLayout: '#0a0a0f',
        colorBorderSecondary: 'rgba(255,255,255,0.06)',
        borderRadius: 12,
        borderRadiusLG: 16,
        fontSize: 14,
        wireframe: false,
      },
      components: {
        Card: {
          colorBgContainer: '#141420',
          borderRadiusLG: 12,
        },
        Menu: {
          colorBgContainer: 'transparent',
          colorItemBgSelected: 'rgba(22,104,220,0.15)',
        },
        Table: {
          colorBgContainer: '#141420',
          headerBg: '#1c1c2e',
        },
        Modal: {
          colorBgElevated: '#1c1c2e',
        },
        Segmented: {
          colorBgLayout: '#141420',
          borderRadiusSM: 8,
        },
      },
    },
  },
  access: {},
  model: {},
  initialState: {},
  request: {},
  layout: {
    title: 'MediaManager',
  },
  routes: [
    {
      path: '/login',
      component: './Auth/Login',
      layout: false,
    },
    {
      path: '/setup',
      component: './Auth/Setup',
      layout: false,
    },
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      name: '仪表盘',
      path: '/dashboard',
      component: './Dashboard',
      icon: 'DashboardOutlined',
    },
    {
      name: '所有媒体',
      path: '/browse',
      component: './Media/Browse',
      icon: 'AppstoreOutlined',
    },
    {
      name: '媒体库',
      path: '/libraries',
      component: './Library/List',
      icon: 'FolderOutlined',
    },
    {
      path: '/libraries/create',
      component: './Library/Create',
    },
    {
      path: '/libraries/:id',
      component: './Library/Detail',
    },
    {
      path: '/media/:id',
      component: './Media/Detail',
    },
    {
      path: '/player/:id',
      component: './Media/Player',
    },
    {
      name: '后台任务',
      path: '/settings/tasks',
      component: './Settings/Tasks',
      icon: 'ControlOutlined',
    },
    {
      name: '标签管理',
      path: '/classification/tags',
      component: './Classification/Tags',
      icon: 'TagsOutlined',
    },
    {
      name: '分类树',
      path: '/classification/categories',
      component: './Classification/Categories',
      icon: 'ClusterOutlined',
    },
    {
      name: '用户管理',
      path: '/users',
      component: './Users/Management',
      icon: 'UserOutlined',
    },
    {
      name: '系统设置',
      path: '/settings',
      component: './Settings/General',
      icon: 'SettingOutlined',
    },
    {
      path: '/settings/rules',
      component: './Settings/Rules',
    },
    {
      path: '/libraries/:id/edit',
      component: './Library/Create',
    },
  ],
  proxy: {
    '/api/v1': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
  npmClient: 'npm',
});
