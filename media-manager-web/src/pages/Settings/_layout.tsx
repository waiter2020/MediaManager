import { history, Outlet, useAccess, useLocation } from '@umijs/max';
import { Layout, Menu } from 'antd';
import React, { useMemo } from 'react';

const { Sider, Content } = Layout;

type MenuItem = {
  key: string;
  label: string;
  show: boolean;
};

const SettingsLayout: React.FC = () => {
  const location = useLocation();
  const access = useAccess();

  const items = useMemo<MenuItem[]>(
    () =>
      [
        { key: '/settings/general', label: '概览', show: !!access.canManageSystem },
        { key: '/settings/security', label: '安全', show: !!access.canManageSystem },
        { key: '/settings/media-processing', label: '媒体处理', show: !!access.canManageSystem },
        { key: '/settings/integrations', label: '集成', show: !!access.canManageSystem },
        { key: '/settings/appearance', label: '外观', show: !!access.canManageSystem },
        { key: '/settings/ai', label: 'AI', show: !!access.canManageSystem },
        { key: '/settings/rules', label: '分类规则', show: !!access.canManageCategories },
        { key: '/settings/tasks', label: '后台任务', show: !!access.canViewTasks },
        { key: '/settings/users', label: '用户管理', show: !!access.canManageUsers },
        { key: '/settings/logs', label: '系统日志', show: !!access.canManageSystem },
        { key: '/settings/scrape-schedules', label: '刮削计划', show: !!access.canManageLibrary },
        { key: '/settings/plugins', label: '插件', show: !!access.canEditLibraryPlugins },
      ].filter((item) => item.show),
    [access],
  );

  const selectedKey = useMemo(() => {
    const match = items.find(
      (item) => location.pathname === item.key || location.pathname.startsWith(`${item.key}/`),
    );
    if (match) return match.key;
    if (location.pathname === '/settings/profile') return '';
    return items[0]?.key ?? '/settings/general';
  }, [items, location.pathname]);

  if (location.pathname === '/settings/profile') {
    return <Outlet />;
  }

  return (
    <Layout style={{ background: 'transparent', minHeight: 'calc(100vh - 120px)' }}>
      <Sider
        width={200}
        style={{
          background: '#141420',
          borderRadius: 8,
          border: '1px solid rgba(255,255,255,0.06)',
          marginRight: 16,
        }}
      >
        <Menu
          mode="inline"
          selectedKeys={selectedKey ? [selectedKey] : []}
          style={{ background: 'transparent', border: 'none' }}
          items={items.map((item) => ({ key: item.key, label: item.label }))}
          onClick={({ key }) => history.push(key)}
        />
      </Sider>
      <Content style={{ flex: 1, minWidth: 0 }}>
        <Outlet />
      </Content>
    </Layout>
  );
};

export default SettingsLayout;
