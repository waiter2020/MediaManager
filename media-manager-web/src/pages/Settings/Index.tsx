import { history, useAccess } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect } from 'react';

const SettingsIndex: React.FC = () => {
  const access = useAccess();

  useEffect(() => {
    const candidates: { path: string; show: boolean }[] = [
      { path: '/settings/general', show: !!access.canManageSystem },
      { path: '/settings/tasks', show: !!access.canViewTasks },
      { path: '/settings/rules', show: !!access.canManageCategories },
      { path: '/settings/users', show: !!access.canManageUsers },
      { path: '/settings/scrape-schedules', show: !!access.canManageLibrary },
      { path: '/settings/plugins', show: !!access.canEditLibraryPlugins },
      { path: '/settings/profile', show: true },
    ];
    const first = candidates.find((c) => c.show);
    history.replace(first?.path ?? '/settings/profile');
  }, [access]);

  return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 80 }} />;
};

export default SettingsIndex;
