import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  PageContainer,
  ProForm,
  ProFormDigit,
  type ProFormInstance,
  ProFormList,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { Alert, Button, Card, Input, message, Space, Spin, Steps, Table, Typography } from 'antd';
import { FolderOpenOutlined } from '@ant-design/icons';
import { history, useParams } from '@umijs/max';
import DirectoryBrowser from '@/components/DirectoryBrowser';
import { createLibrary, getLibrary, updateLibrary, type LibraryUpsertPayload } from '@/services/library';
import type { LibraryPath, LibraryType } from '@/types/library';
import { defaultPluginsForType, type PluginPreviewRow } from '@/utils/libraryPluginDefaults';

interface DirectoryInputProps {
  value?: string;
  onChange?: (value: string) => void;
}

type LibraryFormValues = LibraryUpsertPayload;

const DEFAULT_VALUES: LibraryFormValues = {
  name: '',
  type: 'MOVIE',
  language: 'zh',
  autoScan: true,
  scanIntervalMinutes: 30,
  paths: [],
};

const TYPE_OPTIONS = [
  { label: '电影', value: 'MOVIE' },
  { label: '剧集', value: 'TV_SHOW' },
  { label: '图片', value: 'IMAGE' },
  { label: '音频', value: 'AUDIO' },
  { label: '混合', value: 'MIXED' },
];

const LANGUAGE_OPTIONS = [
  { label: '中文 (zh)', value: 'zh' },
  { label: '英文 (en)', value: 'en' },
  { label: '日文 (ja)', value: 'ja' },
];

const DirectoryInput: React.FC<DirectoryInputProps> = ({ value, onChange }) => {
  const [visible, setVisible] = useState(false);
  return (
    <div className="directory-input-row">
      <Input value={value} onChange={(event) => onChange?.(event.target.value)} placeholder="请输入绝对路径" />
      <Button icon={<FolderOpenOutlined />} onClick={() => setVisible(true)}>
        浏览
      </Button>
      <DirectoryBrowser
        visible={visible}
        onCancel={() => setVisible(false)}
        onSelect={(path) => {
          onChange?.(path);
          setVisible(false);
        }}
      />
    </div>
  );
};

function normalizePaths(paths?: Partial<LibraryPath>[]): LibraryPath[] {
  return (paths || [])
    .map((item, index) => ({
      path: item.path?.trim(),
      priority: item.priority ?? index,
    }))
    .filter((item): item is LibraryPath => Boolean(item.path));
}

const PATH_LIST_RULES = [
  {
    validator: async (_: unknown, value?: Partial<LibraryPath>[]) => {
      if (normalizePaths(value).length === 0) {
        throw new Error('请至少添加一个有效目录');
      }
    },
  },
];

const LibraryCreate: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const libraryId = params.id ? Number(params.id) : undefined;
  const isEdit = Boolean(libraryId);
  const formRef = useRef<ProFormInstance<LibraryFormValues>>();
  const [step, setStep] = useState(0);
  const [formValues, setFormValues] = useState<LibraryFormValues>(DEFAULT_VALUES);
  const [initialValues, setInitialValues] = useState<LibraryFormValues | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isEdit || !libraryId) return;

    setLoading(true);
    getLibrary(libraryId)
      .then((res) => {
        if (res.code === 200 && res.data) {
          const values: LibraryFormValues = {
            name: res.data.name,
            type: res.data.type,
            language: res.data.language || 'zh',
            autoScan: res.data.autoScan ?? true,
            scanIntervalMinutes: res.data.scanIntervalMinutes ?? 30,
            paths: res.data.paths || [],
          };
          setInitialValues(values);
          setFormValues(values);
        } else {
          message.error(res.message || '加载媒体库失败');
        }
      })
      .finally(() => setLoading(false));
  }, [isEdit, libraryId]);

  const pluginPreview = useMemo<PluginPreviewRow[]>(
    () => defaultPluginsForType(formValues.type || 'MOVIE'),
    [formValues.type],
  );

  const buildPayload = (values: LibraryFormValues): LibraryUpsertPayload => ({
    name: values.name?.trim(),
    type: values.type as LibraryType,
    language: values.language,
    autoScan: values.autoScan,
    scanIntervalMinutes: values.scanIntervalMinutes,
    paths: normalizePaths(values.paths),
  });

  const getLatestFormValues = (): LibraryFormValues => ({
    ...DEFAULT_VALUES,
    ...formRef.current?.getFieldsValue(true),
  });

  const syncLatestFormValues = () => {
    const latestValues = getLatestFormValues();
    setFormValues(latestValues);
    return latestValues;
  };

  const markPathError = (errorMessage = '请至少添加一个有效目录') => {
    formRef.current?.setFields([{ name: ['paths'], errors: [errorMessage] }]);
  };

  const handleNext = async () => {
    if (!formRef.current) return;

    try {
      if (step === 0) {
        await formRef.current.validateFields([['name'], ['type']]);
      } else if (step === 1) {
        await formRef.current.validateFields([['paths']]);
      }
    } catch {
      message.warning(step === 0 ? '请填写媒体库名称' : '请至少添加一个有效目录');
      return;
    }

    const latestValues = syncLatestFormValues();
    if (step === 1 && normalizePaths(latestValues.paths).length === 0) {
      markPathError();
      message.warning('请至少添加一个有效目录');
      return;
    }

    setStep((current) => current + 1);
  };

  const submitCreate = async () => {
    const latestValues = syncLatestFormValues();
    if (!latestValues.name?.trim()) {
      message.warning('请填写媒体库名称');
      setStep(0);
      setTimeout(() => {
        formRef.current?.validateFields([['name']]).catch(() => undefined);
      }, 0);
      return;
    }
    const paths = normalizePaths(latestValues.paths);
    if (paths.length === 0) {
      message.warning('请至少添加一个有效目录');
      setStep(1);
      setTimeout(() => markPathError(), 0);
      return;
    }

    setSubmitting(true);
    try {
      const res = await createLibrary({ ...buildPayload(latestValues), paths });
      if (res.code === 200) {
        message.success('媒体库创建成功');
        if (res.data?.id) {
          history.push(`/libraries/${res.data.id}/plugins`);
          message.info('已按库类型应用默认插件，可在插件页微调后扫描');
        } else {
          history.push('/libraries');
        }
      } else {
        message.error(res.message || '创建失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submitEdit = async (values: LibraryFormValues) => {
    if (!libraryId) return false;
    setSubmitting(true);
    try {
      const res = await updateLibrary(libraryId, buildPayload(values));
      if (res.code === 200) {
        message.success('媒体库已更新');
        history.push('/libraries');
        return true;
      }
      message.error(res.message || '更新失败');
      return false;
    } finally {
      setSubmitting(false);
    }
  };

  if (isEdit && loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  }

  if (isEdit) {
    return (
      <PageContainer title="编辑媒体库" onBack={() => history.push('/libraries')}>
        <Card>
          <ProForm<LibraryFormValues>
            onFinish={submitEdit}
            initialValues={initialValues || DEFAULT_VALUES}
            key={initialValues ? 'loaded' : 'new'}
            submitter={{ submitButtonProps: { loading: submitting } }}
          >
            <ProFormText
              name="name"
              label="媒体库名称"
              rules={[{ required: true, whitespace: true, message: '请填写媒体库名称' }]}
            />
            <ProFormSelect name="language" label="元数据语言" options={LANGUAGE_OPTIONS} />
            <ProFormSwitch name="autoScan" label="启用自动扫描" />
            <ProFormDigit name="scanIntervalMinutes" label="扫描间隔（分钟）" min={5} max={1440} />
            <ProFormList
              name="paths"
              label="媒体库目录"
              creatorButtonProps={{ creatorButtonText: '添加目录' }}
              rules={PATH_LIST_RULES}
            >
              <ProForm.Item name="path" rules={[{ required: true, whitespace: true, message: '请输入目录路径' }]}>
                <DirectoryInput />
              </ProForm.Item>
            </ProFormList>
          </ProForm>
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer title="创建媒体库" subTitle="配置基础信息、目录路径，并预览默认插件链。">
      <Steps
        current={step}
        style={{ marginBottom: 24, maxWidth: 720 }}
        items={[{ title: '基础信息' }, { title: '目录路径' }, { title: '插件预览' }]}
      />
      <Card>
        <ProForm<LibraryFormValues>
          formRef={formRef}
          submitter={false}
          preserve
          onValuesChange={syncLatestFormValues}
          initialValues={DEFAULT_VALUES}
        >
          {step === 0 ? (
            <>
              <ProFormText
                name="name"
                label="媒体库名称"
                rules={[{ required: true, whitespace: true, message: '请填写媒体库名称' }]}
              />
              <ProFormSelect
                name="type"
                label="类型"
                options={TYPE_OPTIONS}
                rules={[{ required: true, message: '请选择媒体库类型' }]}
              />
              <ProFormSelect name="language" label="元数据语言" options={LANGUAGE_OPTIONS} />
              <ProFormSwitch name="autoScan" label="启用自动扫描" />
              <ProFormDigit name="scanIntervalMinutes" label="扫描间隔（分钟）" min={5} max={1440} />
            </>
          ) : null}
          {step === 1 ? (
            <ProFormList
              name="paths"
              label="媒体库目录"
              creatorButtonProps={{ creatorButtonText: '添加目录' }}
              rules={PATH_LIST_RULES}
            >
              <ProForm.Item name="path" rules={[{ required: true, whitespace: true, message: '请输入目录路径' }]}>
                <DirectoryInput />
              </ProForm.Item>
            </ProFormList>
          ) : null}
          {step === 2 ? (
            <>
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message="将自动应用以下默认插件"
                description="创建后可在插件配置页增删或调整优先级；TMDb API Key 使用全局集成设置，也可被库级配置覆盖。"
              />
              <Table<PluginPreviewRow>
                size="small"
                pagination={false}
                rowKey={(row) => `${row.pluginId}-${row.kind}`}
                dataSource={pluginPreview}
                columns={[
                  { title: '插件', dataIndex: 'pluginId' },
                  { title: '类型', dataIndex: 'kind', width: 120 },
                  { title: '优先级', dataIndex: 'priority', width: 90 },
                ]}
              />
              <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
                当前库类型：{formValues.type || 'MOVIE'}
              </Typography.Paragraph>
            </>
          ) : null}
        </ProForm>
        <div className="library-form-actions">
          <Button
            disabled={step === 0}
            onClick={() => {
              syncLatestFormValues();
              setStep((current) => current - 1);
            }}
          >
            上一步
          </Button>
          <Space>
            {step < 2 ? (
              <Button type="primary" onClick={handleNext}>
                下一步
              </Button>
            ) : (
              <Button type="primary" loading={submitting} onClick={submitCreate}>
                创建并完成
              </Button>
            )}
          </Space>
        </div>
      </Card>
    </PageContainer>
  );
};

export default LibraryCreate;
