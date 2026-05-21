import React, { useMemo, useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Drawer, Form, Input, Switch, Select, Radio, InputNumber, Space, Tag, message, Popconfirm, Typography } from 'antd';
import { PlusOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { getLibraries } from '@/services/library';
import {
  createScrapeSchedule,
  createScrapeTask,
  deleteScrapeSchedule,
  listScrapeSchedules,
  listScrapeTasksBySchedule,
  runOnceScrapeSchedule,
  updateScrapeSchedule,
  type ScrapeScheduleDto,
  type ScrapeTaskResponse,
} from '@/services/scrape';

const MEDIA_TYPE_OPTIONS = [
  { label: '电影', value: 'MOVIE' },
  { label: '剧集', value: 'TV_SHOW' },
  { label: '剧集单集', value: 'EPISODE' },
  { label: '图片', value: 'IMAGE' },
  { label: '音频', value: 'AUDIO' },
];

const TARGET_STATUS_OPTIONS = [
  { label: '未识别', value: 'UNIDENTIFIED' },
  { label: '已识别', value: 'IDENTIFIED' },
  { label: '全部', value: 'ALL' },
];

function safeParseJsonArray(value?: string): string[] {
  if (!value) return [];
  try {
    const arr = JSON.parse(value);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function formatIso(iso?: string) {
  if (!iso) return '-';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('zh-CN');
}

const SchedulesPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<ScrapeScheduleDto | null>(null);
  const [form] = Form.useForm();
  const [libraries, setLibraries] = useState<any[]>([]);
  const [tasksDrawerOpen, setTasksDrawerOpen] = useState(false);
  const [tasksSchedule, setTasksSchedule] = useState<ScrapeScheduleDto | null>(null);
  const [tasks, setTasks] = useState<ScrapeTaskResponse[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);

  const fetchLibraries = async () => {
    const res = await getLibraries();
    if (res?.code === 200) setLibraries(res.data || []);
  };

  const libraryOptions = useMemo(
    () => (libraries || []).map((l: any) => ({ label: l.name, value: l.id })),
    [libraries],
  );

  const openCreate = async () => {
    await fetchLibraries();
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      name: '每日刮削',
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

  const openEdit = async (record: any) => {
    await fetchLibraries();
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      mediaTypes: safeParseJsonArray(record.mediaTypes),
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: ScrapeScheduleDto = {
      name: values.name,
      enabled: !!values.enabled,
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

    if (editing?.id) {
      const res = await updateScrapeSchedule(editing.id, payload);
      if (res?.code === 200) {
        message.success('已更新');
        setDrawerOpen(false);
        actionRef.current?.reload();
      } else {
        message.error(res?.message || '更新失败');
      }
      return;
    }
    const res = await createScrapeSchedule(payload);
    if (res?.code === 200) {
      message.success('已创建');
      setDrawerOpen(false);
      actionRef.current?.reload();
    } else {
      message.error(res?.message || '创建失败');
    }
  };

  const openTasks = async (record: any) => {
    setTasksSchedule(record);
    setTasksDrawerOpen(true);
    setTasksLoading(true);
    try {
      const res = await listScrapeTasksBySchedule(record.id);
      if (res?.code === 200) setTasks(res.data || []);
    } finally {
      setTasksLoading(false);
    }
  };

  const columns: ProColumns[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    {
      title: '名称',
      dataIndex: 'name',
      width: 180,
      render: (_, r: any) => (
        <Space size={6}>
          <span>{r.name}</span>
          {r.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'scheduleType',
      width: 110,
      search: false,
      render: (_, r: any) => (r.scheduleType === 'CRON' ? 'Cron' : '固定间隔'),
    },
    {
      title: '表达式/间隔',
      dataIndex: 'cronExpr',
      width: 220,
      search: false,
      render: (_, r: any) =>
        r.scheduleType === 'CRON' ? (
          <Typography.Text code>{r.cronExpr || '-'}</Typography.Text>
        ) : (
          <span>{r.intervalSeconds || 0}s</span>
        ),
    },
    {
      title: '范围',
      dataIndex: 'scope',
      width: 150,
      search: false,
      render: (_, r: any) =>
        r.scope === 'LIBRARY' ? `媒体库 #${r.libraryId || '-'}` : '全局',
    },
    {
      title: '目标',
      dataIndex: 'targetStatus',
      width: 110,
      search: false,
      render: (_, r: any) => r.targetStatus || '-',
    },
    {
      title: '下次执行',
      dataIndex: 'nextRunAt',
      width: 170,
      search: false,
      render: (_, r: any) => formatIso(r.nextRunAt),
    },
    {
      title: '最近执行',
      dataIndex: 'lastRunAt',
      width: 170,
      search: false,
      render: (_, r: any) => formatIso(r.lastRunAt),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      render: (_, record: any) => [
        <a key="tasks" onClick={() => openTasks(record)}>
          任务历史
        </a>,
        <a key="run" onClick={async () => {
          const res = await runOnceScrapeSchedule(record.id);
          if (res?.code === 200) {
            message.success('已触发一次执行');
            actionRef.current?.reload();
          } else {
            message.error(res?.message || '触发失败');
          }
        }}>
          立即执行
        </a>,
        <a key="edit" onClick={() => openEdit(record)}>编辑</a>,
        <Popconfirm
          key="del"
          title="确定删除该刮削计划？"
          onConfirm={async () => {
            const res = await deleteScrapeSchedule(record.id);
            if (res?.code === 200) {
              message.success('已删除');
              actionRef.current?.reload();
            } else {
              message.error(res?.message || '删除失败');
            }
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  const taskColumns: ProColumns[] = [
    { title: '任务ID', dataIndex: 'id', width: 80, search: false },
    { title: '状态', dataIndex: 'status', width: 120, search: false },
    { title: '触发', dataIndex: 'triggerType', width: 100, search: false },
    { title: '总数', dataIndex: 'totalItems', width: 80, search: false },
    { title: '已处理', dataIndex: 'scrapedItems', width: 80, search: false },
    { title: '错误', dataIndex: 'errorItems', width: 80, search: false },
    { title: '开始', dataIndex: 'startedAt', width: 170, search: false, render: (_, r: any) => formatIso(r.startedAt) },
    { title: '结束', dataIndex: 'finishedAt', width: 170, search: false, render: (_, r: any) => formatIso(r.finishedAt) },
  ];

  return (
    <PageContainer title="刮削计划">
      <ProTable
        actionRef={actionRef}
        rowKey="id"
        search={false}
        columns={columns}
        request={async () => {
          const res = await listScrapeSchedules();
          return { data: res?.data || [], success: true };
        }}
        toolBarRender={() => [
          <Button key="reload" icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
          <Button
            key="scrape-now"
            icon={<PlayCircleOutlined />}
            onClick={async () => {
              const res = await createScrapeTask({ targetStatus: 'UNIDENTIFIED' });
              if (res?.code === 200) {
                message.success(res.data ? `刮削任务 #${res.data.id} 已创建` : '刮削已提交');
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
        <Form layout="vertical" form={form}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="例如：每日刮削未识别" />
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

          <Form.Item shouldUpdate>
            {() => {
              const type = form.getFieldValue('scheduleType');
              if (type === 'FIXED_DELAY') {
                return (
                  <Form.Item
                    name="intervalSeconds"
                    label="间隔（秒）"
                    rules={[{ required: true, type: 'number', min: 1 }]}
                  >
                    <InputNumber style={{ width: '100%' }} />
                  </Form.Item>
                );
              }
              return (
                <Form.Item name="cronExpr" label="Cron 表达式" rules={[{ required: true }]}>
                  <Input placeholder="0 0 4 * * ?" />
                </Form.Item>
              );
            }}
          </Form.Item>

          <Form.Item name="scope" label="范围" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { label: '全局', value: 'GLOBAL' },
                { label: '指定媒体库', value: 'LIBRARY' },
              ]}
            />
          </Form.Item>

          <Form.Item shouldUpdate>
            {() => {
              const scope = form.getFieldValue('scope');
              if (scope !== 'LIBRARY') return null;
              return (
                <Form.Item name="libraryId" label="媒体库" rules={[{ required: true }]}>
                  <Select options={libraryOptions} showSearch optionFilterProp="label" />
                </Form.Item>
              );
            }}
          </Form.Item>

          <Form.Item name="targetStatus" label="目标状态" rules={[{ required: true }]}>
            <Select options={TARGET_STATUS_OPTIONS} />
          </Form.Item>

          <Form.Item name="mediaTypes" label="媒体类型（可多选）">
            <Select mode="multiple" options={MEDIA_TYPE_OPTIONS} />
          </Form.Item>

          <Form.Item name="maxConcurrency" label="规则并发上限" rules={[{ required: true, type: 'number', min: 1 }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="batchSizeOverride" label="批大小（可选）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="requestDelayMsOverride" label="请求间隔 ms（可选）">
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
            <Button
              icon={<ReloadOutlined />}
              loading={tasksLoading}
              onClick={async () => {
                if (!tasksSchedule?.id) return;
                setTasksLoading(true);
                try {
                  const res = await listScrapeTasksBySchedule(tasksSchedule.id);
                  if (res?.code === 200) setTasks(res.data || []);
                } finally {
                  setTasksLoading(false);
                }
              }}
            >
              刷新
            </Button>
            {tasksSchedule?.id ? (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={async () => {
                  const res = await runOnceScrapeSchedule(tasksSchedule.id!);
                  if (res?.code === 200) {
                    message.success('已触发一次执行');
                  } else {
                    message.error(res?.message || '触发失败');
                  }
                }}
              >
                立即执行
              </Button>
            ) : null}
          </Space>
        }
      >
        <ProTable
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

