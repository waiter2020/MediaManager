import { PageContainer, ProForm, ProFormText } from '@ant-design/pro-components';
import { Alert, Button, Card, Form, Space, Tag, Typography, message } from 'antd';
import { ApiOutlined, CloudSyncOutlined, KeyOutlined, SettingOutlined } from '@ant-design/icons';
import React, { useCallback, useEffect, useState } from 'react';
import { history, useAccess } from '@umijs/max';
import { getIntegrationsSettings, updateIntegrationsSettings } from '@/services/settings';

type IntegrationsFormValues = {
  tmdbApiKey?: string;
};

const IntegrationsSettingsPage: React.FC = () => {
  const access = useAccess();
  const [form] = Form.useForm<IntegrationsFormValues>();
  const [loading, setLoading] = useState(true);
  const [configured, setConfigured] = useState(false);

  const applyConfiguredState = useCallback(
    (nextConfigured: boolean) => {
      setConfigured(nextConfigured);
      form.setFieldsValue({ tmdbApiKey: nextConfigured ? '***' : '' });
    },
    [form],
  );

  const load = useCallback(() => {
    if (!access.canManageSystem) {
      setLoading(false);
      return;
    }
    setLoading(true);
    getIntegrationsSettings()
      .then((res) => {
        if (res.code === 200 && res.data) {
          applyConfiguredState(Boolean(res.data.tmdbApiKeyConfigured));
        }
      })
      .finally(() => setLoading(false));
  }, [access.canManageSystem, applyConfiguredState]);

  useEffect(() => {
    load();
  }, [load]);

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
      subTitle="外部元数据源和全局凭据"
      extra={
        <Space>
          <Button icon={<CloudSyncOutlined />} onClick={() => history.push('/settings/tasks')}>
            任务监控
          </Button>
          <Button icon={<SettingOutlined />} onClick={() => history.push('/settings/plugins')}>
            插件
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card>
          <Space size={[12, 12]} wrap>
            <Tag icon={<KeyOutlined />} color={configured ? 'success' : 'warning'}>
              TMDb {configured ? '已配置' : '未配置'}
            </Tag>
            <Tag icon={<ApiOutlined />} color="blue">
              库级插件可覆盖
            </Tag>
            <Tag icon={<CloudSyncOutlined />} color="purple">
              刮削任务使用
            </Tag>
          </Space>
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            全局 TMDb Key 会作为 TMDb 刮削器默认凭据；媒体库插件配置中的 Key 优先级更高。
          </Typography.Paragraph>
        </Card>

        {configured ? (
          <Alert
            type="info"
            showIcon
            message="已保存 TMDb Key"
            description="留空或保留 *** 不会覆盖现有密钥；输入新值后保存会替换全局密钥。"
          />
        ) : (
          <Alert
            type="warning"
            showIcon
            message="TMDb Key 未配置"
            description="启用 TMDb 刮削器后，如果库级配置也没有 Key，远程元数据不会被拉取。"
          />
        )}

        <Card>
          <ProForm<IntegrationsFormValues>
            form={form}
            loading={loading}
            onFinish={async (values) => {
              const res = await updateIntegrationsSettings({ tmdbApiKey: values.tmdbApiKey });
              if (res.code === 200) {
                message.success('已保存');
                applyConfiguredState(Boolean(res.data?.tmdbApiKeyConfigured));
              }
            }}
            submitter={{ searchConfig: { submitText: '保存' } }}
          >
            <ProFormText.Password
              name="tmdbApiKey"
              label="TMDb API Key"
              placeholder={configured ? '留空保留现有密钥' : '输入 API Key'}
              fieldProps={{ autoComplete: 'new-password' }}
            />
          </ProForm>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default IntegrationsSettingsPage;
