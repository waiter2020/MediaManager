import React, { useEffect, useMemo, useState } from 'react';
import { PageContainer, ProForm, ProFormDigit, ProFormSelect, ProFormSwitch, ProFormText } from '@ant-design/pro-components';
import { Alert, Button, Card, Divider, Space, Tag, Typography, message } from 'antd';
import { useAccess } from '@umijs/max';
import {
  formatEmbeddingDimensions,
  getAiConfig,
  getAiHealth,
  listAiProviders,
  updateAiConfig,
  type AiConfigPayload,
  type AiHealth,
  type AiProviderDescriptor,
} from '@/services/ai';
import { runReindexWithPolling, type ReindexStatus } from '@/utils/reindexPoll';

const AiSettings: React.FC = () => {
  const access = useAccess();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [providers, setProviders] = useState<AiProviderDescriptor[]>([]);
  const [config, setConfig] = useState<AiConfigPayload | null>(null);
  const [llmProviderId, setLlmProviderId] = useState('ollama');
  const [embedProviderId, setEmbedProviderId] = useState('ollama');
  const [aiHealth, setAiHealth] = useState<AiHealth | null>(null);
  const [healthLoading, setHealthLoading] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [reindexProgress, setReindexProgress] = useState<ReindexStatus | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [providerRes, configRes] = await Promise.all([listAiProviders(), getAiConfig()]);
      if (providerRes.code === 200) setProviders(providerRes.data || []);
      if (configRes.code === 200 && configRes.data) {
        setConfig(configRes.data);
        setLlmProviderId(configRes.data.llmProvider || configRes.data.defaultProvider || 'ollama');
        setEmbedProviderId(configRes.data.embedProvider || configRes.data.defaultProvider || 'ollama');
      }
    } finally {
      setLoading(false);
    }
  };

  const runHealth = async (refresh = false) => {
    setHealthLoading(true);
    try {
      const res = await getAiHealth({ refresh });
      if (res.code === 200) setAiHealth(res.data);
    } catch {
      message.error('健康检查失败');
    } finally {
      setHealthLoading(false);
    }
  };

  useEffect(() => {
    if (access.canManageSystem) {
      load().then(() => runHealth(true));
    }
  }, [access.canManageSystem]);

  const providerOptions = useMemo(
    () =>
      providers.map((provider) => ({
        label: `${provider.displayName}${provider.local ? '（本地）' : '（云端）'}`,
        value: provider.id,
      })),
    [providers],
  );

  const llmUsesOpenAi = llmProviderId === 'openai-compatible';
  const embedUsesOpenAi = embedProviderId === 'openai-compatible';
  const usesOllama = llmProviderId === 'ollama' || embedProviderId === 'ollama';

  if (!access.canManageSystem) {
    return (
      <PageContainer title="AI 设置">
        <Alert type="warning" message="需要 system:manage 权限才能配置 AI 提供方" />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="AI 提供方设置"
      subTitle="分别选择 LLM 与向量 Provider，并配置 Ollama 或 OpenAI 兼容端点。库级配置可在媒体库插件配置中覆盖。"
    >
      <Card loading={loading} style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            保存后会自动执行健康检查。语义搜索依赖向量 Provider，自然语言查询与 AI 打标依赖 LLM Provider。
          </Typography.Paragraph>
          <Space>
            <Button loading={healthLoading} onClick={() => runHealth(true)}>
              检测连接
            </Button>
            <Button
              loading={reindexing}
              onClick={async () => {
                setReindexing(true);
                setReindexProgress(null);
                try {
                  const finalStatus = await runReindexWithPolling(setReindexProgress);
                  if (finalStatus.state === 'done') {
                    message.success(
                      `索引已重建：FTS ${finalStatus.ftsIndexed ?? 0}，向量 ${finalStatus.embedIndexed ?? 0}`,
                    );
                  } else {
                    message.error(finalStatus.message || '重建索引失败');
                  }
                } catch (e: unknown) {
                  message.error((e as { message?: string })?.message || '重建索引失败');
                } finally {
                  setReindexing(false);
                }
              }}
            >
              重建搜索索引
            </Button>
          </Space>
          {reindexProgress?.state === 'running' && (
            <Typography.Text type="secondary">
              {reindexProgress.message || '重建中...'}
            </Typography.Text>
          )}
          {aiHealth && (
            <Alert
              type={aiHealth.status === 'ok' ? 'success' : 'warning'}
              showIcon
              message={
                <>
                  <Tag color={aiHealth.status === 'ok' ? 'success' : 'warning'}>
                    {aiHealth.status === 'ok' ? '正常' : '降级'}
                  </Tag>
                  <Typography.Text style={{ marginLeft: 8 }}>
                    向量 {String(aiHealth.embedProviderName || aiHealth.displayName || aiHealth.embedProvider || aiHealth.provider || '-')} /{' '}
                    {String(aiHealth.embedModel || '-')}，LLM {String(aiHealth.llmProviderName || aiHealth.llmProvider || '-')} /{' '}
                    {String(aiHealth.llmModel || '-')}，维度{' '}
                    {formatEmbeddingDimensions(aiHealth.embeddingDimensions)}
                  </Typography.Text>
                  {aiHealth.message && (
                    <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
                      {aiHealth.message}
                    </Typography.Paragraph>
                  )}
                </>
              }
            />
          )}
        </Space>
      </Card>

      <Card loading={loading}>
        {config && (
          <ProForm<AiConfigPayload>
            initialValues={{
              ...config,
              llmProvider: config.llmProvider || config.defaultProvider || 'ollama',
              embedProvider: config.embedProvider || config.defaultProvider || 'ollama',
            }}
            onValuesChange={(_, all) => {
              if (all.llmProvider) setLlmProviderId(all.llmProvider);
              if (all.embedProvider) setEmbedProviderId(all.embedProvider);
            }}
            submitter={{
              searchConfig: { submitText: '保存 AI 配置' },
              submitButtonProps: { loading: saving },
            }}
            onFinish={async (values) => {
              setSaving(true);
              try {
                const payload: AiConfigPayload = {
                  ...config,
                  ...values,
                  defaultProvider: values.llmProvider || values.defaultProvider || config.defaultProvider,
                };
                const res = await updateAiConfig(payload);
                if (res.code === 200) {
                  message.success('AI 配置已保存');
                  setConfig(res.data);
                  setLlmProviderId(res.data.llmProvider || res.data.defaultProvider || 'ollama');
                  setEmbedProviderId(res.data.embedProvider || res.data.defaultProvider || 'ollama');
                  await runHealth(true);
                }
              } finally {
                setSaving(false);
              }
            }}
          >
            <ProFormSelect
              name="llmProvider"
              label="LLM 提供方"
              options={providerOptions}
              rules={[{ required: true }]}
              extra="自然语言查询、元数据补全和 AI 打标会使用此提供方"
            />
            <ProFormText name="llmModel" label="LLM 模型" rules={[{ required: true }]} />

            <ProFormSelect
              name="embedProvider"
              label="向量提供方"
              options={providerOptions}
              rules={[{ required: true }]}
              extra="语义搜索、相似推荐和向量索引会使用此提供方"
            />
            <ProFormText name="embedModel" label="Embedding 模型" rules={[{ required: true }]} />

            {llmUsesOpenAi && (
              <>
                <Divider orientation="left">LLM OpenAI 兼容 API</Divider>
                <ProFormText
                  name="openaiLlmBaseUrl"
                  label="LLM API Base URL"
                  placeholder="https://api.openai.com/v1"
                  rules={[{ required: true }]}
                />
                <ProFormText.Password
                  name="openaiLlmApiKey"
                  label="LLM API Key"
                  placeholder="留空则不修改已保存的 Key"
                  extra={config.openaiLlmApiKey === '***' ? '已配置 LLM Key，输入新值可覆盖' : undefined}
                />
              </>
            )}

            {embedUsesOpenAi && (
              <>
                <Divider orientation="left">向量 OpenAI 兼容 API</Divider>
                <ProFormText
                  name="openaiEmbedBaseUrl"
                  label="Embedding API Base URL"
                  placeholder="https://api.openai.com/v1"
                  rules={[{ required: true }]}
                />
                <ProFormText.Password
                  name="openaiEmbedApiKey"
                  label="Embedding API Key"
                  placeholder="留空则不修改已保存的 Key"
                  extra={config.openaiEmbedApiKey === '***' ? '已配置向量 Key，输入新值可覆盖' : undefined}
                />
              </>
            )}

            {usesOllama && (
              <>
                <Divider orientation="left">Ollama 本地</Divider>
                <ProFormText
                  name="ollamaBaseUrl"
                  label="Ollama 服务地址"
                  placeholder="http://host.docker.internal:11434"
                  rules={[{ required: true }]}
                  extra="Docker 容器内不要使用 localhost；留空则使用环境变量 MEDIAMANAGER_AI_OLLAMA_BASE_URL"
                />
              </>
            )}

            <Divider orientation="left">行为</Divider>

            <ProFormSwitch
              name="classifierEnabled"
              label="启用 AI 自动打标建议"
              fieldProps={{ checkedChildren: '开', unCheckedChildren: '关' }}
            />
            <ProFormSwitch
              name="outboundAllowed"
              label="允许云端 Provider"
              fieldProps={{ checkedChildren: '允许', unCheckedChildren: '仅本地' }}
              extra="关闭后，选择 openai-compatible 的任务会降级为 noop"
            />
            <ProFormDigit
              name="timeoutMs"
              label="请求超时（毫秒）"
              min={5000}
              max={600000}
              fieldProps={{ style: { width: 200 } }}
            />

            {providers.length > 0 && (
              <Alert
                type="info"
                showIcon
                style={{ marginTop: 8 }}
                message="库级覆盖"
                description={
                  <>
                    在“媒体库 / 插件配置”中添加类型为 <Tag>AI_PROVIDER</Tag> 的插件，例如{' '}
                    <code>{llmProviderId}</code> 或 <code>{embedProviderId}</code>，可为本库单独指定地址与模型。
                  </>
                }
              />
            )}
          </ProForm>
        )}
      </Card>
    </PageContainer>
  );
};

export default AiSettings;
