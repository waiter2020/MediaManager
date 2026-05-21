import { PageContainer, ProForm, ProFormText, ProFormSwitch, ProFormDigit } from '@ant-design/pro-components';
import { Alert, Button, Card, Divider, message, Space, Spin, Tag, Typography } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { request, useAccess } from '@umijs/max';
import { getAiHealth } from '@/services/ai';
import { reindexSearch } from '@/services/search';

type FieldType = 'text' | 'boolean' | 'number';

type ConfigGroup = 'general' | 'storage' | 'transcode' | 'security' | 'logging' | 'tasks' | 'advanced';

interface SystemConfig {
  key: string;
  value: string;
  description?: string;
}

interface EnhancedConfig extends SystemConfig {
  fieldType: FieldType;
  group: ConfigGroup;
}

const GROUP_DEFINITIONS: { key: ConfigGroup; title: string; description?: string }[] = [
  {
    key: 'general',
    title: '基础与通用设置',
    description: '影响系统整体行为的通用配置，例如站点信息、语言、默认行为等。',
  },
  {
    key: 'storage',
    title: '存储与路径',
    description: '媒体库根目录、缓存目录、临时文件目录等与磁盘路径相关的配置。',
  },
  {
    key: 'transcode',
    title: '转码与处理',
    description: '转码开关、并发度、预设等与媒体处理相关的配置。',
  },
  {
    key: 'security',
    title: '用户与安全',
    description: '认证、令牌、密码策略等安全相关配置。',
  },
  {
    key: 'logging',
    title: '日志与监控',
    description: '日志级别、日志目录以及监控相关参数。',
  },
  {
    key: 'tasks',
    title: '任务与扫描',
    description: '后台扫描任务、定时任务等相关配置。',
  },
  {
    key: 'advanced',
    title: '高级与实验特性',
    description: '不常用或可能带来较大影响的高级参数，修改前请确保理解含义。',
  },
];

const detectFieldType = (value: string | undefined | null): FieldType => {
  if (value === undefined || value === null) {
    return 'text';
  }
  const normalized = String(value).toLowerCase();
  if (normalized === 'true' || normalized === 'false') {
    return 'boolean';
  }
  if (!Number.isNaN(Number(value)) && value.trim() !== '') {
    return 'number';
  }
  return 'text';
};

const detectGroup = (key: string): ConfigGroup => {
  const k = key.toLowerCase();
  if (k.startsWith('storage.') || k.includes('path') || k.includes('directory') || k.includes('folder') || k.includes('root')) {
    return 'storage';
  }
  if (k.includes('transcode') || k.includes('encode') || k.includes('ffmpeg')) {
    return 'transcode';
  }
  if (k.includes('auth') || k.includes('security') || k.includes('jwt') || k.includes('token') || k.includes('password')) {
    return 'security';
  }
  if (k.includes('log') || k.includes('monitor') || k.includes('metric')) {
    return 'logging';
  }
  if (k.includes('scan') || k.includes('task') || k.includes('job') || k.includes('queue')) {
    return 'tasks';
  }
  if (k.includes('advanced') || k.includes('experimental') || k.includes('debug')) {
    return 'advanced';
  }
  return 'general';
};

const enhanceConfig = (c: SystemConfig): EnhancedConfig => {
  const fieldType = detectFieldType(c.value);
  const group = detectGroup(c.key);
  return { ...c, fieldType, group };
};

const GeneralSettings: React.FC = () => {
  const access = useAccess();
  const [configs, setConfigs] = useState<SystemConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [isDirty, setIsDirty] = useState(false);
  const [aiHealth, setAiHealth] = useState<{ provider?: string; embeddingDimensions?: number; status?: string } | null>(null);
  const [aiHealthLoading, setAiHealthLoading] = useState(false);
  const [reindexing, setReindexing] = useState(false);

  const runAiHealthCheck = async () => {
    setAiHealthLoading(true);
    setAiHealth(null);
    try {
      const res = await getAiHealth();
      if (res.code === 200) {
        setAiHealth(res.data || null);
      } else {
        message.error(res.message || '检测失败');
      }
    } catch {
      message.error('AI 健康检查失败');
    } finally {
      setAiHealthLoading(false);
    }
  };

  useEffect(() => {
    request('/api/v1/system/config', { method: 'GET' })
      .then((res) => {
        if (res.code === 200 && Array.isArray(res.data)) {
          setConfigs(res.data || []);
        }
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (access.canManageSystem) {
      runAiHealthCheck();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access.canManageSystem]);

  const enhancedConfigs = useMemo<EnhancedConfig[]>(() => configs.map(enhanceConfig), [configs]);

  const initialValues = useMemo<Record<string, any>>(() => {
    const map: Record<string, any> = {};
    enhancedConfigs.forEach((c) => {
      if (c.fieldType === 'boolean') {
        map[c.key] = String(c.value).toLowerCase() === 'true';
      } else if (c.fieldType === 'number') {
        const n = Number(c.value);
        map[c.key] = Number.isNaN(n) ? undefined : n;
      } else {
        map[c.key] = c.value;
      }
    });
    return map;
  }, [enhancedConfigs]);

  const groupedConfigs = useMemo<Record<ConfigGroup, EnhancedConfig[]>>(() => {
    const grouped: Record<ConfigGroup, EnhancedConfig[]> = {
      general: [],
      storage: [],
      transcode: [],
      security: [],
      logging: [],
      tasks: [],
      advanced: [],
    };
    enhancedConfigs.forEach((c) => {
      grouped[c.group].push(c);
    });
    return grouped;
  }, [enhancedConfigs]);

  const handleSave = async (values: Record<string, any>) => {
    const payload: Record<string, string> = {};
    enhancedConfigs.forEach((c) => {
      const raw = values[c.key];
      if (c.fieldType === 'boolean') {
        payload[c.key] = raw ? 'true' : 'false';
      } else if (c.fieldType === 'number') {
        payload[c.key] = raw === undefined || raw === null ? '' : String(raw);
      } else {
        payload[c.key] = raw ?? '';
      }
    });

    setSaving(true);
    try {
      await request('/api/v1/system/config', { method: 'PUT', data: payload });
      message.success('配置已保存');
      setLastSavedAt(new Date());
      setIsDirty(false);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  }

  const handleValuesChange = (_: any, allValues: any) => {
    const hasChanges = JSON.stringify(allValues) !== JSON.stringify(initialValues);
    setIsDirty(hasChanges);
  };

  const renderField = (c: EnhancedConfig) => {
    const label = c.description || c.key;
    if (c.fieldType === 'boolean') {
      return (
        <ProFormSwitch
          key={c.key}
          name={c.key}
          label={label}
          fieldProps={{
            checkedChildren: '开启',
            unCheckedChildren: '关闭',
          }}
        />
      );
    }

    if (c.fieldType === 'number') {
      return (
        <ProFormDigit
          key={c.key}
          name={c.key}
          label={label}
          fieldProps={{
            style: { width: 200 },
          }}
        />
      );
    }

    return <ProFormText key={c.key} name={c.key} label={label} />;
  };

  return (
    <PageContainer
      title="系统设置"
      content="集中管理 MediaManager 的全局行为与默认配置。请谨慎修改，部分设置可能会影响整个系统的稳定性。"
    >
      {access.canManageSystem && (
        <Card title="AI 服务状态" style={{ marginBottom: 16 }} loading={aiHealthLoading}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              检测当前配置的嵌入模型是否可用。
            </Typography.Paragraph>
            <Space>
              <Button loading={aiHealthLoading} onClick={runAiHealthCheck}>
                重新检测
              </Button>
              <Button
                loading={reindexing}
                onClick={async () => {
                  setReindexing(true);
                  try {
                    const res = await reindexSearch();
                    if (res.code === 200) {
                      message.success(`全文索引已重建：${res.data?.indexed ?? 0} 条`);
                    }
                  } catch {
                    message.error('重建索引失败');
                  } finally {
                    setReindexing(false);
                  }
                }}
              >
                重建搜索索引
              </Button>
            </Space>
            {aiHealth ? (
              <Alert
                type={aiHealth.status === 'ok' ? 'success' : 'warning'}
                showIcon
                message={
                  <>
                    <Tag color={aiHealth.status === 'ok' ? 'success' : 'warning'}>
                      {aiHealth.status === 'ok' ? '正常' : '降级'}
                    </Tag>
                    <Typography.Text style={{ marginLeft: 8 }}>
                      提供商：{aiHealth.provider || '-'} · 向量维度：{aiHealth.embeddingDimensions ?? '-'}
                    </Typography.Text>
                  </>
                }
              />
            ) : !aiHealthLoading ? (
              <Typography.Text type="secondary">无法获取 AI 健康状态</Typography.Text>
            ) : null}
          </Space>
        </Card>
      )}

      <Card>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
          {lastSavedAt
            ? `上次保存时间：${lastSavedAt.toLocaleString()}`
            : '当前为从服务器加载的配置。修改后请点击右下角“保存更改”按钮生效。'}
        </Typography.Paragraph>

        {isDirty && (
          <Alert
            type="warning"
            showIcon
            message="有未保存的更改"
            description="配置修改仅在保存成功后才会生效。请在完成修改后点击表单底部的“保存更改”按钮。"
            style={{ marginBottom: 16 }}
          />
        )}

        <ProForm
          initialValues={initialValues}
          onFinish={handleSave}
          onValuesChange={handleValuesChange}
          submitter={{
            searchConfig: {
              submitText: '保存更改',
            },
            submitButtonProps: {
              loading: saving,
              type: 'primary',
            },
            resetButtonProps: {
              children: '重置为当前服务器配置',
            },
          }}
        >
          {GROUP_DEFINITIONS.map((group) => {
            const list = groupedConfigs[group.key];
            if (!list || list.length === 0) {
              return null;
            }
            return (
              <React.Fragment key={group.key}>
                <Divider orientation="left" style={{ marginTop: 24 }}>
                  {group.title}
                </Divider>
                {group.description && (
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
                    {group.description}
                  </Typography.Paragraph>
                )}
                {list.map((c) => renderField(c))}
              </React.Fragment>
            );
          })}
        </ProForm>
      </Card>
    </PageContainer>
  );
};

export default GeneralSettings;
