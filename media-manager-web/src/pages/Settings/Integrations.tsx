import { PageContainer, ProForm, ProFormText } from '@ant-design/pro-components';
import { Alert, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import { getIntegrationsSettings, updateIntegrationsSettings } from '@/services/settings';

const IntegrationsSettingsPage: React.FC = () => {
  const access = useAccess();
  const [loading, setLoading] = useState(true);
  const [configured, setConfigured] = useState(false);
  const [initial, setInitial] = useState<{ tmdbApiKey?: string }>({});

  useEffect(() => {
    if (!access.canManageSystem) return;
    getIntegrationsSettings()
      .then((res) => {
        if (res.code === 200 && res.data) {
          setConfigured(!!res.data.tmdbApiKeyConfigured);
          setInitial({ tmdbApiKey: res.data.tmdbApiKeyConfigured ? '***' : '' });
        }
      })
      .finally(() => setLoading(false));
  }, [access.canManageSystem]);

  if (!access.canManageSystem) {
    return (
      <PageContainer title="集成">
        <Alert type="warning" message="需要 system:manage 权限" />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="集成"
      subTitle="全局 TMDb API Key；各媒体库可在插件配置中覆盖。"
    >
      {configured && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="已配置 TMDb Key"
          description="留空或填写 *** 将保留现有密钥；填写新值将覆盖。"
        />
      )}
      <ProForm
        loading={loading}
        initialValues={initial}
        onFinish={async (values) => {
          const res = await updateIntegrationsSettings({ tmdbApiKey: values.tmdbApiKey });
          if (res.code === 200) {
            message.success('已保存');
            setConfigured(!!res.data?.tmdbApiKeyConfigured);
            setInitial({
              tmdbApiKey: res.data?.tmdbApiKeyConfigured ? '***' : '',
            });
          }
        }}
        submitter={{ searchConfig: { submitText: '保存' } }}
      >
        <ProFormText.Password
          name="tmdbApiKey"
          label="TMDb API Key"
          placeholder={configured ? '留空保留现有密钥' : '输入 API Key'}
        />
      </ProForm>
    </PageContainer>
  );
};

export default IntegrationsSettingsPage;
