import { PageContainer, ProForm, ProFormText } from '@ant-design/pro-components';
import { Alert, Button, Card, Form, Space, Tag, Typography, message } from 'antd';
import { ApiOutlined, CloudSyncOutlined, KeyOutlined, SettingOutlined, TranslationOutlined } from '@ant-design/icons';
import React, { useCallback, useEffect, useState } from 'react';
import { history, useAccess } from '@umijs/max';
import { getIntegrationsSettings, updateIntegrationsSettings } from '@/services/settings';

type IntegrationsFormValues = {
  tmdbApiKey?: string;
  opensubtitlesApiKey?: string;
  opensubtitlesUsername?: string;
  opensubtitlesPassword?: string;
  subtitleDefaultLanguage?: string;
};

const IntegrationsSettingsPage: React.FC = () => {
  const access = useAccess();
  const [form] = Form.useForm<IntegrationsFormValues>();
  const [loading, setLoading] = useState(true);
  const [tmdbConfigured, setTmdbConfigured] = useState(false);
  const [opensubtitlesConfigured, setOpensubtitlesConfigured] = useState(false);
  const [opensubtitlesPasswordConfigured, setOpensubtitlesPasswordConfigured] = useState(false);

  const applyConfiguredState = useCallback(
    (settings: {
      tmdbApiKeyConfigured?: boolean;
      opensubtitlesApiKeyConfigured?: boolean;
      opensubtitlesUsername?: string;
      opensubtitlesPasswordConfigured?: boolean;
      subtitleDefaultLanguage?: string;
    }) => {
      const nextTmdbConfigured = Boolean(settings.tmdbApiKeyConfigured);
      const nextOpensubtitlesConfigured = Boolean(settings.opensubtitlesApiKeyConfigured);
      setTmdbConfigured(nextTmdbConfigured);
      setOpensubtitlesConfigured(nextOpensubtitlesConfigured);
      setOpensubtitlesPasswordConfigured(Boolean(settings.opensubtitlesPasswordConfigured));
      form.setFieldsValue({
        tmdbApiKey: nextTmdbConfigured ? '***' : '',
        opensubtitlesApiKey: nextOpensubtitlesConfigured ? '***' : '',
        opensubtitlesUsername: settings.opensubtitlesUsername || '',
        opensubtitlesPassword: settings.opensubtitlesPasswordConfigured ? '***' : '',
        subtitleDefaultLanguage: settings.subtitleDefaultLanguage || 'zh-CN',
      });
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
          applyConfiguredState(res.data);
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
            <Tag icon={<KeyOutlined />} color={tmdbConfigured ? 'success' : 'warning'}>
              TMDb {tmdbConfigured ? '已配置' : '未配置'}
            </Tag>
            <Tag icon={<TranslationOutlined />} color={opensubtitlesConfigured ? 'success' : 'warning'}>
              OpenSubtitles {opensubtitlesConfigured ? '已配置' : '未配置'}
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
            OpenSubtitles 用于在线字幕搜索与下载，需在 opensubtitles.com 申请 API Key，并填写账号密码。
          </Typography.Paragraph>
        </Card>

        {!tmdbConfigured ? (
          <Alert
            type="warning"
            showIcon
            message="TMDb Key 未配置"
            description="启用 TMDb 刮削器后，如果库级配置也没有 Key，远程元数据不会被拉取。"
          />
        ) : (
          <Alert
            type="info"
            showIcon
            message="已保存 TMDb Key"
            description="留空或保留 *** 不会覆盖现有密钥；输入新值后保存会替换全局密钥。"
          />
        )}

        {!opensubtitlesConfigured ? (
          <Alert
            type="warning"
            showIcon
            message="OpenSubtitles 未配置"
            description="播放页与详情页的字幕搜索需要 API Key；下载字幕还需要账号和密码。"
          />
        ) : null}

        <Card title="TMDb">
          <ProForm<IntegrationsFormValues>
            form={form}
            loading={loading}
            onFinish={async (values) => {
              const res = await updateIntegrationsSettings({
                tmdbApiKey: values.tmdbApiKey,
                opensubtitlesApiKey: values.opensubtitlesApiKey,
                opensubtitlesUsername: values.opensubtitlesUsername,
                opensubtitlesPassword: values.opensubtitlesPassword,
                subtitleDefaultLanguage: values.subtitleDefaultLanguage,
              });
              if (res.code === 200) {
                message.success('已保存');
                applyConfiguredState(res.data || {});
              }
            }}
            submitter={{ searchConfig: { submitText: '保存全部集成配置' } }}
          >
            <ProFormText.Password
              name="tmdbApiKey"
              label="TMDb API Key"
              placeholder={tmdbConfigured ? '留空保留现有密钥' : '输入 API Key'}
              fieldProps={{ autoComplete: 'new-password' }}
            />
            <ProFormText.Password
              name="opensubtitlesApiKey"
              label="OpenSubtitles API Key"
              placeholder={opensubtitlesConfigured ? '留空保留现有密钥' : '输入 API Key'}
              fieldProps={{ autoComplete: 'new-password' }}
            />
            <ProFormText
              name="opensubtitlesUsername"
              label="OpenSubtitles 用户名"
              placeholder="下载字幕所需账号"
            />
            <ProFormText.Password
              name="opensubtitlesPassword"
              label="OpenSubtitles 密码"
              placeholder={opensubtitlesPasswordConfigured ? '留空保留现有密码' : '输入密码'}
              fieldProps={{ autoComplete: 'new-password' }}
            />
            <ProFormText
              name="subtitleDefaultLanguage"
              label="默认字幕语言"
              placeholder="zh-CN"
            />
          </ProForm>
        </Card>
      </Space>
    </PageContainer>
  );
};

export default IntegrationsSettingsPage;
