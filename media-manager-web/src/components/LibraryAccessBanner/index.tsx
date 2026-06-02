import React, { useEffect, useState } from 'react';
import { Alert, Button } from 'antd';
import { history, useAccess } from '@umijs/max';
import { getSystemInfo } from '@/services/system';

/**
 * Shown when the user has no viewable media libraries (common misconfiguration for USER/GUEST).
 */
const LibraryAccessBanner: React.FC = () => {
  const access = useAccess();
  const [visible, setVisible] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getSystemInfo()
      .then((res) => {
        if (cancelled || res?.code !== 200) return;
        const data = res.data || {};
        setVisible(data.hasViewableLibraries === false);
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading || !visible) {
    return null;
  }

  return (
    <Alert
      type="warning"
      showIcon
      closable
      style={{ margin: '12px 24px 0' }}
      message="当前账号没有可访问的媒体库"
      description={
        access.canManageUsers
          ? '非管理员用户需在「用户管理 → 库权限」中勾选可查看的媒体库，否则浏览、搜索与发现页将为空。'
          : '请联系管理员在「用户管理」中为您分配媒体库查看权限。'
      }
      action={
        access.canManageUsers ? (
          <Button size="small" type="primary" onClick={() => history.push('/settings/users')}>
            用户管理
          </Button>
        ) : undefined
      }
    />
  );
};

export default LibraryAccessBanner;
