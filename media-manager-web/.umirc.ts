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
      name: '搜索',
      path: '/search',
      component: './Search',
      icon: 'SearchOutlined',
      access: 'canViewMedia',
    },
    {
      name: '所有媒体',
      path: '/browse',
      component: './Media/Browse',
      icon: 'AppstoreOutlined',
      access: 'canViewMedia',
    },
    {
      name: '媒体库',
      path: '/libraries',
      component: './Library/List',
      icon: 'FolderOutlined',
      access: 'canViewLibrary',
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
      path: '/libraries/:id/plugins',
      component: './Library/Plugins',
      access: 'canManageLibrary',
    },
    {
      path: '/media/:id',
      component: './Media/Detail',
      access: 'canViewMedia',
    },
    {
      path: '/player/:id',
      component: './Media/Player',
      access: 'canPlayMedia',
    },
    {
      name: '后台任务',
      path: '/settings/tasks',
      component: './Settings/Tasks',
      icon: 'ThunderboltOutlined',
      access: 'canViewTasks',
    },
    {
      name: '标签管理',
      path: '/classification/tags',
      component: './Classification/Tags',
      icon: 'TagsOutlined',
      access: 'canManageTags',
    },
    {
      name: '分类树',
      path: '/classification/categories',
      component: './Classification/Categories',
      icon: 'ClusterOutlined',
      access: 'canManageCategories',
    },
    {
      name: '用户管理',
      path: '/users',
      component: './Users/Management',
      icon: 'UserOutlined',
      access: 'canManageUsers',
    },
    {
      name: '系统设置',
      path: '/settings',
      component: './Settings/General',
      icon: 'SettingOutlined',
      access: 'canManageSystem',
    },
    {
      path: '/settings/rules',
      component: './Settings/Rules',
    },
    {
      path: '/libraries/:id/edit',
      component: './Library/Create',
    },
    {
      name: '系统日志',
      path: '/system/logs',
      component: './System/Logs',
      icon: 'FileSearchOutlined',
      access: 'canManageSystem',
    },
    {
      name: '刮削计划',
      path: '/scrape/schedules',
      component: './Scrape/Schedules',
      icon: 'ScheduleOutlined',
      access: 'canManageLibrary',
    },
    {
      name: '回收站',
      path: '/recycle-bin',
      component: './RecycleBin',
      icon: 'DeleteOutlined',
      access: 'canDeleteMedia',
    },
    {
      name: 'AI 审核',
      path: '/intelligence/review',
      component: './Intelligence/Review',
      icon: 'RobotOutlined',
      access: 'canEditMetadata',
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
