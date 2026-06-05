import { PageContainer, ProForm, ProFormSelect } from '@ant-design/pro-components';
import { Alert, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAccess, useModel } from '@umijs/max';
import { getAppearanceSettings, updateAppearanceSettings } from '@/services/settings';
import { applyThemePreference } from '@/utils/theme';

const AppearanceSettingsPage: React.FC = () => {
  const access = useAccess();
  const { initialState, setInitialState } = useModel('@@initialState');
  const [loading, setLoading] = useState(true);
  const [initial, setInitial] = useState<{ theme?: string }>({ theme: 'dark' });

  useEffect(() => {
    if (!access.canManageSystem) return;
    getAppearanceSettings()
      .then((res) => {
        if (res.code === 200 && res.data?.theme) {
          setInitial({ theme: res.data.theme });
        }
      })
      .finally(() => setLoading(false));
  }, [access.canManageSystem]);

  if (!access.canManageSystem) {
    return (
      <PageContainer title="外观">
        <Alert type="warning" message="需要 system:manage 权限" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="外观" subTitle="全局默认主题，保存后立即应用到当前浏览器。">
      <ProForm
        loading={loading}
        initialValues={initial}
        onFinish={async (values) => {
          const res = await updateAppearanceSettings({ theme: values.theme });
          if (res.code === 200 && res.data?.theme) {
            message.success('已保存');
            setInitial({ theme: res.data.theme });
            applyThemePreference(res.data.theme);
            setInitialState({
              ...initialState,
              theme: res.data.theme,
            });
          }
        }}
        submitter={{ searchConfig: { submitText: '保存' } }}
      >
        <ProFormSelect
          name="theme"
          label="默认主题"
          options={[
            { label: '深色', value: 'dark' },
            { label: '浅色', value: 'light' },
            { label: '跟随系统', value: 'system' },
          ]}
          rules={[{ required: true }]}
        />
      </ProForm>
    </PageContainer>
  );
};

export default AppearanceSettingsPage;
