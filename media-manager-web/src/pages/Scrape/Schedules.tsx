import React, { useMemo, useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  message,
  Popconfirm,
  Radio,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { getLibraries, getLibrary } from '@/services/library';
import {
  createScrapeSchedule,
  deleteScrapeSchedule,
  listScrapeSchedules,
  listScrapeTasksBySchedule,
  runOnceScrapeSchedule,
  startScrapeAll,
  updateScrapeSchedule,
  type ScrapeScheduleDto,
  type ScrapeTaskResponse,
} from '@/services/scrape';
import type { MediaLibrary } from '@/types/library';
import { pluginKindLabel } from '@/utils/pluginLabels';

type ScheduleFormValues = Omit<ScrapeScheduleDto, 'mediaTypes'> & {
  mediaTypes?: string[];
};

const MEDIA_TYPE_OPTIONS = [
  { label: '电影', value: 'MOVIE' },
  { label: '剧集', value: 'TV_SHOW' },
  { label: '单集', value: 'EPISODE' },
  { label: '图片', value: 'IMAGE' },
  { label: '音频', value: 'AUDIO' },
];

const TARGET_STATUS_OPTIONS = [
  { label: '未识别', value: 'UNIDENTIFIED' },
  { label: '已识别', value: 'IDENTIFIED' },
  { label: '全部', value: 'ALL' },
];

function parseMediaTypes(value?: string): string[] {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

function formatIso(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}

const SchedulesPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm<ScheduleFormValues>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<ScrapeScheduleDto | null>(null);
  const [libraries, setLibraries] = useState<Pick<MediaLibrary, 'id' | 'name'>[]>([]);
  const [tasksDrawerOpen, setTasksDrawerOpen] = useState(false);
  const [tasksSchedule, setTasksSchedule] = useState<ScrapeScheduleDto | null>(null);
  const [tasks, setTasks] = useState<ScrapeTaskResponse[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [libraryScrapers, setLibraryScrapers] = useState<string[]>([]);
  const watchedLibraryId = Form.useWatch('libraryId', form);
  const watchedScope = Form.useWatch('scope', form);

  const libraryOptions = useMemo(
    () => libraries.map((library) => ({ label: library.name, value: library.id })),
    [libraries],
  );

  const fetchLibraries = async () => {
    const res = await getLibraries();
    if (res.code === 200) {
      setLibraries((res.data || []).map((library) => ({ id: library.id, name: library.name })));
    }
  };

  React.useEffect(() => {
    if (watchedScope !== 'LIBRARY' || !watchedLibraryId) {
      setLibraryScrapers([]);
      return;
    }

    getLibrary(watchedLibraryId).then((res) => {
      if (res.code !== 200 || !Array.isArray(res.data?.plugins)) {
        setLibraryScrapers([]);
        return;
      }

      setLibraryScrapers(
        res.data.plugins
          .filter((plugin) => plugin.kind === 'SCRAPER' && plugin.enabled !== false)
          .map((plugin) => plugin.pluginId),
      );
    });
  }, [watchedScope, watchedLibraryId]);

  const openCreate = async () => {
    await fetchLibraries();
    setEditing(null);
    form.setFieldsValue({
      name: '每日刮削未识别媒体',
      enabled: true,
      scheduleType: 'CRON',
      cronExpr: '0 0 4 * * ?',
      scope: 'GLOBAL',
      targetStatus: 'UNIDENTIFIED',
      mediaTypes: ['MOVIE', 'TV_SHOW'],
      maxConcurrency: 1,
    });
    setDrawerOpen(true);
  };

  const openEdit = async (record: ScrapeScheduleDto) => {
    await fetchLibraries();
    setEditing(record);
    form.setFieldsValue({
      ...record,
      mediaTypes: parseMediaTypes(record.mediaTypes),
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: ScrapeScheduleDto = {
      name: values.name,
      enabled: values.enabled !== false,
      scheduleType: values.scheduleType,
      cronExpr: values.scheduleType === 'CRON' ? values.cronExpr : undefined,
      intervalSeconds: values.scheduleType === 'FIXED_DELAY' ? values.intervalSeconds : undefined,
      scope: values.scope,
      libraryId: values.scope === 'LIBRARY' ? values.libraryId : undefined,
      targetStatus: values.targetStatus,
      mediaTypes: JSON.stringify(values.mediaTypes || []),
      maxConcurrency: values.maxConcurrency ?? 1,
      batchSizeOverride: values.batchSizeOverride,
      requestDelayMsOverride: values.requestDelayMsOverride,
    };

    const res = editing?.id
      ? await updateScrapeSchedule(editing.id, payload)
      : await createScrapeSchedule(payload);
    if (res.code === 200) {
      message.success(editing ? '计划已更新' : '计划已创建');
      setDrawerOpen(false);
      actionRef.current?.reload();
    } else {
      message.error(res.message || '保存失败');
    }
  };

  const openTasks = async (record: ScrapeScheduleDto) => {
    if (!record.id) return;
    setTasksSchedule(record);
    setTasksDrawerOpen(true);
    setTasksLoading(true);
    try {
      const res = await listScrapeTasksBySchedule(record.id);
      if (res.code === 200) setTasks(res.data || []);
    } finally {
      setTasksLoading(false);
    }
  };

  const reloadTasks = async () => {
    if (!tasksSchedule?.id) return;
    setTasksLoading(true);
    try {
      const res = await listScrapeTasksBySchedule(tasksSchedule.id);
      if (res.code === 200) setTasks(res.data || []);
    } finally {
      setTasksLoading(false);
    }
  };

  const columns: ProColumns<ScrapeScheduleDto>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    {
      title: '名称',
      dataIndex: 'name',
      width: 190,
      render: (_, record) => (
        <Space size={6}>
          <span>{record.name}</span>
          {record.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
        </Space>
      ),
    },
    {
      title: '调度',
      dataIndex: 'scheduleType',
      width: 120,
      search: false,
      renderText: (_, record) => (record.scheduleType === 'CRON' ? 'Cron' : '固定间隔'),
    },
    {
      title: '表达式/间隔',
      dataIndex: 'cronExpr',
      width: 220,
      search: false,
      render: (_, record) =>
        record.scheduleType === 'CRON' ? (
          <Typography.Text code>{record.cronExpr || '-'}</Typography.Text>
        ) : (
          <span>{record.intervalSeconds || 0}s</span>
        ),
    },
    {
      title: '范围',
      dataIndex: 'scope',
      width: 150,
      search: false,
      renderText: (_, record) => (record.scope === 'LIBRARY' ? `媒体库 #${record.libraryId || '-'}` : '全局'),
    },
    { title: '目标', dataIndex: 'targetStatus', width: 110, search: false },
    {
      title: '下次执行',
      dataIndex: 'nextRunAt',
      width: 170,
      search: false,
      renderText: (value) => formatIso(value as string | undefined),
    },
    {
      title: '最近执行',
      dataIndex: 'lastRunAt',
      width: 170,
      search: false,
      renderText: (value) => formatIso(value as string | undefined),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      render: (_, record) => [
        <a key="tasks" onClick={() => openTasks(record)}>
          任务历史
        </a>,
        <a
          key="run"
          onClick={async () => {
            if (!record.id) return;
            const res = await runOnceScrapeSchedule(record.id);
            if (res.code === 200) {
              message.success('已触发一次执行');
              actionRef.current?.reload();
            } else {
              message.error(res.message || '触发失败');
            }
          }}
        >
          立即执行
        </a>,
        <a key="edit" onClick={() => openEdit(record)}>
          编辑
        </a>,
        <Popconfirm
          key="del"
          title="确定删除该刮削计划？"
          onConfirm={async () => {
            if (!record.id) return;
            const res = await deleteScrapeSchedule(record.id);
            if (res.code === 200) {
              message.success('已删除');
              actionRef.current?.reload();
            } else {
              message.error(res.message || '删除失败');
            }
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  const taskColumns: ProColumns<ScrapeTaskResponse>[] = [
    { title: '任务 ID', dataIndex: 'id', width: 90, search: false },
    { title: '状态', dataIndex: 'status', width: 120, search: false },
    { title: '触发', dataIndex: 'triggerType', width: 100, search: false },
    { title: '总数', dataIndex: 'totalItems', width: 80, search: false },
    { title: '已处理', dataIndex: 'scrapedItems', width: 80, search: false },
    { title: '错误', dataIndex: 'errorItems', width: 80, search: false },
    {
      title: '开始',
      dataIndex: 'startedAt',
      width: 170,
      search: false,
      renderText: (value) => formatIso(value as string | undefined),
    },
    {
      title: '结束',
      dataIndex: 'finishedAt',
      width: 170,
      search: false,
      renderText: (value) => formatIso(value as string | undefined),
    },
  ];

  return (
    <PageContainer
      title="刮削计划"
      subTitle="按 Cron 或固定间隔触发 SCRAPER 插件，补齐媒体在线元数据。"
      extra={<Button onClick={() => history.push('/settings/tasks')}>任务监控</Button>}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="前置条件"
        description="目标媒体库需要在插件配置中启用 SCRAPER，例如 TMDb。手动立即执行需要 task:execute 或 library:edit 权限。"
      />
      <ProTable<ScrapeScheduleDto>
        actionRef={actionRef}
        rowKey="id"
        search={false}
        columns={columns}
        request={async () => {
          const res = await listScrapeSchedules();
          return { data: res.data || [], success: res.code === 200 };
        }}
        toolBarRender={() => [
          <Button key="reload" icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
          <Button
            key="scrape-now"
            icon={<PlayCircleOutlined />}
            onClick={async () => {
              const res = await startScrapeAll('UNIDENTIFIED');
              if (res.code === 200) {
                if (res.data?.id) {
                  message.success(`全库刮削任务 #${res.data.id} 已创建`);
                  history.push('/settings/tasks');
                } else {
                  message.info('当前没有可刮削的媒体项');
                }
              } else {
                message.error(res.message || '创建任务失败');
              }
            }}
          >
            立即全库刮削
          </Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建计划
          </Button>,
        ]}
      />

      <Drawer
        title={editing?.id ? '编辑刮削计划' : '新建刮削计划'}
        width={520}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" onClick={submit}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<ScheduleFormValues> layout="vertical" form={form}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例如：每日刮削未识别媒体" />
          </Form.Item>

          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Form.Item name="scheduleType" label="调度类型" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { label: 'Cron', value: 'CRON' },
                { label: '固定间隔', value: 'FIXED_DELAY' },
              ]}
            />
          </Form.Item>

          <Form.Item noStyle shouldUpdate>
            {() =>
              form.getFieldValue('scheduleType') === 'FIXED_DELAY' ? (
                <Form.Item
                  name="intervalSeconds"
                  label="间隔（秒）"
                  rules={[{ required: true, type: 'number', min: 1 }]}
                >
                  <InputNumber style={{ width: '100%' }} />
                </Form.Item>
              ) : (
                <Form.Item name="cronExpr" label="Cron 表达式" rules={[{ required: true }]}>
                  <Input placeholder="0 0 4 * * ?" />
                </Form.Item>
              )
            }
          </Form.Item>

          <Form.Item name="scope" label="范围" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { label: '全局', value: 'GLOBAL' },
                { label: '指定媒体库', value: 'LIBRARY' },
              ]}
            />
          </Form.Item>

          <Form.Item noStyle shouldUpdate>
            {() =>
              form.getFieldValue('scope') === 'LIBRARY' ? (
                <>
                  <Form.Item name="libraryId" label="媒体库" rules={[{ required: true }]}>
                    <Select options={libraryOptions} showSearch optionFilterProp="label" />
                  </Form.Item>
                  {watchedLibraryId ? (
                    libraryScrapers.length > 0 ? (
                      <Alert
                        type="success"
                        showIcon
                        style={{ marginBottom: 16 }}
                        message="已启用的刮削器"
                        description={
                          <Space wrap>
                            {libraryScrapers.map((pluginId) => (
                              <Tag key={pluginId}>
                                {pluginId} - {pluginKindLabel('SCRAPER')}
                              </Tag>
                            ))}
                          </Space>
                        }
                      />
                    ) : (
                      <Alert
                        type="warning"
                        showIcon
                        style={{ marginBottom: 16 }}
                        message="该库未启用 SCRAPER 插件"
                        description="保存计划前请在该库的插件配置中启用 TMDb 等刮削器，否则服务端会拒绝创建 LIBRARY 计划。"
                        action={
                          <Button size="small" onClick={() => history.push(`/libraries/${watchedLibraryId}/plugins`)}>
                            插件配置
                          </Button>
                        }
                      />
                    )
                  ) : null}
                </>
              ) : null
            }
          </Form.Item>

          <Form.Item name="targetStatus" label="目标状态" rules={[{ required: true }]}>
            <Select options={TARGET_STATUS_OPTIONS} />
          </Form.Item>

          <Form.Item name="mediaTypes" label="媒体类型">
            <Select mode="multiple" options={MEDIA_TYPE_OPTIONS} />
          </Form.Item>

          <Form.Item name="maxConcurrency" label="并发上限" rules={[{ required: true, type: 'number', min: 1 }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="batchSizeOverride" label="批大小">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="requestDelayMsOverride" label="请求间隔（ms）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={`任务历史：${tasksSchedule?.name || ''}`}
        width={860}
        open={tasksDrawerOpen}
        onClose={() => setTasksDrawerOpen(false)}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} loading={tasksLoading} onClick={reloadTasks}>
              刷新
            </Button>
            {tasksSchedule?.id ? (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={async () => {
                  const res = await runOnceScrapeSchedule(tasksSchedule.id!);
                  if (res.code === 200) {
                    message.success('已触发一次执行');
                    reloadTasks();
                  } else {
                    message.error(res.message || '触发失败');
                  }
                }}
              >
                立即执行
              </Button>
            ) : null}
          </Space>
        }
      >
        <ProTable<ScrapeTaskResponse>
          rowKey="id"
          search={false}
          loading={tasksLoading}
          columns={taskColumns}
          dataSource={tasks}
          pagination={{ pageSize: 10 }}
          options={false}
        />
      </Drawer>
    </PageContainer>
  );
};

export default SchedulesPage;
