import { PageContainer, ProForm, ProFormSwitch } from '@ant-design/pro-components';
import { Alert, Form, message, Space, Tag } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import { getSecuritySettings, updateSecuritySettings } from '@/services/settings';
import type { SecuritySettings } from '@/services/settings';

type SecurityFormValues = {
  authEnabled?: boolean;
};

const authText = (enabled?: boolean) => (enabled ? '开启' : '关闭');

const AuthStatusTag: React.FC<{ label: string; enabled?: boolean }> = ({ label, enabled }) => (
  <Tag color={enabled ? 'green' : 'default'}>
    {label}：{authText(enabled)}
  </Tag>
);

const SecuritySettingsPage: React.FC = () => {
  const access = useAccess();
  const [form] = Form.useForm<SecurityFormValues>();
  const [loading, setLoading] = useState(true);
  const [initial, setInitial] = useState<SecurityFormValues>({});
  const [settings, setSettings] = useState<SecuritySettings>();

  const applySettings = useCallback((next: SecuritySettings) => {
    const values = { authEnabled: next.authEnabled };
    setSettings(next);
    setInitial(values);
    form.setFieldsValue(values);
  }, [form]);

  useEffect(() => {
    if (!access.canManageSystem) {
      setLoading(false);
      return;
    }
    getSecuritySettings()
      .then((res) => {
        if (res.code === 200 && res.data) {
          applySettings(res.data);
        }
      })
      .finally(() => setLoading(false));
  }, [access.canManageSystem, applySettings]);

  if (!access.canManageSystem) {
    return (
      <PageContainer title="安全">
        <Alert type="warning" message="需要 system:manage 权限" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="安全" subTitle="认证与访问控制相关配置">
      <Alert
        type={settings?.restartRequired ? 'warning' : 'info'}
        showIcon
        style={{ marginBottom: 16 }}
        message={
          <Space wrap>
            <span>
              {!settings
                ? '正在读取登录认证状态'
                : settings.restartRequired
                  ? '登录认证配置待重启'
                  : '登录认证配置已同步'}
            </span>
            {settings && (
              <>
                <AuthStatusTag label="当前生效" enabled={settings.effectiveAuthEnabled} />
                <AuthStatusTag label="保存配置" enabled={settings.authEnabled} />
              </>
            )}
          </Space>
        }
        description={
          !settings
            ? undefined
            : settings.restartRequired
            ? 'Security 过滤器链在启动时读取 auth.enabled，重启应用后才会切换到保存配置。'
            : '修改「启用登录认证」后需重启应用才会影响当前访问控制。'
        }
      />
      <ProForm
        form={form}
        loading={loading}
        initialValues={initial}
        onFinish={async (values) => {
          const res = await updateSecuritySettings({ authEnabled: values.authEnabled });
          if (res.code === 200 && res.data) {
            applySettings(res.data);
            if (res.data.restartRequired) {
              message.warning('已保存，重启应用后生效');
            } else {
              message.success('已保存');
            }
          }
        }}
        submitter={{ searchConfig: { submitText: '保存' } }}
      >
        <ProFormSwitch
          name="authEnabled"
          label="启用登录认证"
          extra={
            settings
              ? `当前生效：${authText(settings.effectiveAuthEnabled)}；保存配置：${authText(settings.authEnabled)}`
              : undefined
          }
          fieldProps={{ checkedChildren: '开启', unCheckedChildren: '关闭' }}
        />
      </ProForm>
    </PageContainer>
  );
};

export default SecuritySettingsPage;
