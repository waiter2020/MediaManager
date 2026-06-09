import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  InputNumber,
  List,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CloudSyncOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  StopOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { history, useAccess, useModel } from '@umijs/max';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  cancelScrapeTask,
  createScrapeTask,
  getScrapeTask,
  listScrapeTasks,
  previewScrapeTask,
  type ScrapeMediaType,
  type ScrapeTargetStatus,
  type ScrapeTaskPreviewResponse,
  type ScrapeTaskResponse,
} from '@/services/scrape';
import LibraryScanModal from '@/components/LibraryScanModal';
import { cancelScan, getLibraries } from '@/services/library';
import type { MediaLibrary } from '@/types/library';
import type { ScanProgress } from '@/models/global';

type ScrapeLaunchValues = {
  scope: 'GLOBAL' | 'LIBRARY';
  libraryId?: number;
  targetStatus: ScrapeTargetStatus;
  mediaTypes?: ScrapeMediaType[];
  batchSize?: number;
  requestDelayMs?: number;
};

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
};

const TARGET_STATUS_OPTIONS = [
  { label: '未识别', value: 'UNIDENTIFIED' },
  { label: '已识别', value: 'IDENTIFIED' },
  { label: '全部', value: 'ALL' },
];

const MEDIA_TYPE_OPTIONS = [
  { label: '电影', value: 'MOVIE' },
  { label: '剧集', value: 'TV_SHOW' },
  { label: '单集', value: 'EPISODE' },
  { label: '图片', value: 'IMAGE' },
  { label: '音频', value: 'AUDIO' },
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

function taskPercent(task: ScrapeTaskResponse) {
  if (typeof task.progressPercent === 'number') {
    return task.progressPercent;
  }
  const total = task.totalItems ?? 0;
  if (total <= 0) return 0;
  const processed = (task.scrapedItems ?? 0) + (task.errorItems ?? 0);
  return Math.min(100, Math.round((processed / total) * 100));
}

function scanErrorTooltip(scan: ScanProgress) {
  const errors = scan.recentErrors || [];
  if (errors.length === 0) {
    return <span>暂无失败明细，请查看系统日志</span>;
  }
  return <span style={{ whiteSpace: 'pre-line' }}>{errors.map((item) => `${item.path}: ${item.message}`).join('\n')}</span>;
}

const Tasks: React.FC = () => {
  const access = useAccess();
  const { scanStatus, recentEvents, fetchScanSnapshot } = useModel('global');
  const [scrapeForm] = Form.useForm<ScrapeLaunchValues>();
  const [scrapeTasks, setScrapeTasks] = useState<ScrapeTaskResponse[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [startingScrape, setStartingScrape] = useState(false);
  const [scanModalOpen, setScanModalOpen] = useState(false);
  const [libraries, setLibraries] = useState<Pick<MediaLibrary, 'id' | 'name'>[]>([]);
  const [selectedScanLibraryId, setSelectedScanLibraryId] = useState<number | undefined>();
  const [detailTask, setDetailTask] = useState<ScrapeTaskResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [preview, setPreview] = useState<ScrapeTaskPreviewResponse>();
  const [previewLoading, setPreviewLoading] = useState(false);
  const watchedScope = Form.useWatch('scope', scrapeForm);
  const watchedLibraryId = Form.useWatch('libraryId', scrapeForm);
  const watchedTargetStatus = Form.useWatch('targetStatus', scrapeForm);
  const watchedMediaTypes = Form.useWatch('mediaTypes', scrapeForm);

  const scanEntries = Object.values(scanStatus || {}) as ScanProgress[];

  const libraryOptions = useMemo(
    () => libraries.map((lib) => ({ label: lib.name, value: lib.id })),
    [libraries],
  );

  const loadScrapeTasks = useCallback(async () => {
    setTasksLoading(true);
    try {
      const res = await listScrapeTasks();
      if (res.code === 200) {
        setScrapeTasks(res.data || []);
      }
    } finally {
      setTasksLoading(false);
    }
  }, []);

  const loadLibraries = useCallback(async () => {
    const res = await getLibraries();
    if (res.code === 200) {
      const next = (res.data || []).map((lib) => ({ id: lib.id, name: lib.name }));
      setLibraries(next);
      if (!selectedScanLibraryId && next.length > 0) {
        setSelectedScanLibraryId(next[0].id);
      }
    }
  }, [selectedScanLibraryId]);

  const refreshAll = useCallback(() => {
    loadScrapeTasks();
    fetchScanSnapshot?.();
  }, [fetchScanSnapshot, loadScrapeTasks]);

  const currentScrapePayload = useCallback(() => {
    const values = scrapeForm.getFieldsValue();
    return {
      libraryId: values.scope === 'LIBRARY' ? values.libraryId : undefined,
      targetStatus: values.targetStatus,
      mediaTypes: values.mediaTypes,
      batchSize: values.batchSize,
      requestDelayMs: values.requestDelayMs,
    };
  }, [scrapeForm]);

  const refreshPreview = useCallback(async () => {
    const values = scrapeForm.getFieldsValue();
    if (values.scope === 'LIBRARY' && !values.libraryId) {
      setPreview(undefined);
      return;
    }
    if (!values.targetStatus) {
      return;
    }
    setPreviewLoading(true);
    try {
      const res = await previewScrapeTask(currentScrapePayload());
      if (res.code === 200 && res.data) {
        setPreview(res.data);
      }
    } finally {
      setPreviewLoading(false);
    }
  }, [currentScrapePayload, scrapeForm]);

  useEffect(() => {
    scrapeForm.setFieldsValue({
      scope: 'GLOBAL',
      targetStatus: 'ALL',
      mediaTypes: undefined,
      batchSize: 50,
      requestDelayMs: 2000,
    });
    loadScrapeTasks();
    loadLibraries();
    fetchScanSnapshot?.();
  }, [fetchScanSnapshot, loadLibraries, loadScrapeTasks, scrapeForm]);

  useEffect(() => {
    loadScrapeTasks();
  }, [loadScrapeTasks, recentEvents?.length]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      refreshPreview();
    }, 250);
    return () => window.clearTimeout(timer);
  }, [refreshPreview, watchedScope, watchedLibraryId, watchedTargetStatus, watchedMediaTypes]);

  const handleStartScan = () => {
    if (!selectedScanLibraryId) {
      message.warning('请选择媒体库');
      return;
    }
    setScanModalOpen(true);
  };

  const handleStartScrape = async () => {
    const values = await scrapeForm.validateFields();
    if (values.scope === 'LIBRARY' && !values.libraryId) {
      message.warning('请选择媒体库');
      return;
    }
    if (preview && preview.totalItems <= 0) {
      message.warning(preview.tips?.[0] || '当前没有符合条件的媒体项');
      return;
    }
    setStartingScrape(true);
    try {
      const res = await createScrapeTask(currentScrapePayload());
      if (res.code === 200) {
        if (res.data?.id) {
          message.success(`刮削任务 #${res.data.id} 已创建`);
          loadScrapeTasks();
        } else {
          message.info(preview?.tips?.[0] || '当前没有符合条件的媒体项');
          refreshPreview();
        }
      }
    } finally {
      setStartingScrape(false);
    }
  };

  const openTaskDetail = async (taskId: number) => {
    const res = await getScrapeTask(taskId);
    if (res.code === 200 && res.data) {
      setDetailTask(res.data);
      setDetailOpen(true);
    }
  };

  const scanColumns: ColumnsType<ScanProgress> = [
    {
      title: '媒体库',
      dataIndex: 'libraryName',
      width: 180,
      render: (name: string, row) => name || `库 #${row.libraryId}`,
    },
    {
      title: '结果',
      width: 360,
      render: (_, row) => (
        <Space size={[6, 6]} wrap>
          <Tag color="processing">检查 {row.scannedFiles ?? 0}</Tag>
          <Tag color="blue">匹配 {row.matchedFiles ?? 0}</Tag>
          <Tag color="green">新增 {row.newItems ?? 0}</Tag>
          <Tag>更新 {row.updatedItems ?? 0}</Tag>
          <Tag>恢复 {row.restoredItems ?? 0}</Tag>
          {(row.failedItems ?? 0) > 0 ? (
            <Tooltip title={scanErrorTooltip(row)}>
              <Tag color="error">失败 {row.failedItems}</Tag>
            </Tooltip>
          ) : null}
        </Space>
      ),
    },
    {
      title: '当前路径',
      dataIndex: 'currentPath',
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) =>
        access.canScanLibrary ? (
          <Popconfirm
            title="取消该扫描任务？"
            onConfirm={async () => {
              const res = await cancelScan(row.libraryId);
              if (res.code === 200) {
                message.success('已请求取消扫描');
                fetchScanSnapshot?.();
              }
            }}
          >
            <Button danger size="small" icon={<StopOutlined />}>
              取消
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  const taskColumns: ColumnsType<ScrapeTaskResponse> = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    {
      title: '范围',
      width: 170,
      render: (_, row) => row.libraryName || '全部可见媒体库',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 105,
      render: (status: string) => <Tag color={STATUS_COLOR[status] || 'default'}>{status}</Tag>,
    },
    {
      title: '进度',
      width: 210,
      render: (_, row) => {
        const percent = taskPercent(row);
        const status = row.status === 'RUNNING' ? 'active' : row.status === 'FAILED' ? 'exception' : 'normal';
        return (
          <div style={{ width: 170 }}>
            <Progress percent={percent} size="small" status={status} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              成功 {row.scrapedItems ?? 0} / 错误 {row.errorItems ?? 0} / 总数 {row.totalItems ?? 0}
            </Typography.Text>
          </div>
        );
      },
    },
    {
      title: '目标',
      width: 210,
      render: (_, row) => (
        <Space size={[4, 4]} wrap>
          <Tag>{row.targetStatus || 'UNIDENTIFIED'}</Tag>
          {parseMediaTypes(row.mediaTypes).map((type) => (
            <Tag key={type} color="blue">
              {type}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '参数',
      width: 150,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text type="secondary">批量 {row.batchSize ?? '-'}</Typography.Text>
          <Typography.Text type="secondary">间隔 {row.requestDelayMs ?? '-'}ms</Typography.Text>
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (value: string) => formatIso(value),
    },
    {
      title: '操作',
      width: 150,
      render: (_, row) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openTaskDetail(row.id)}>
            详情
          </Button>
          {row.status === 'RUNNING' || row.status === 'PENDING' ? (
            <Popconfirm
              title="取消此刮削任务？"
              onConfirm={async () => {
                const res = await cancelScrapeTask(row.id);
                if (res.code === 200) {
                  message.success('已请求取消');
                  loadScrapeTasks();
                }
              }}
            >
              <Button type="link" danger size="small">
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const displayEvents = useMemo(
    () => (recentEvents || []).map((event) => ({ time: event.time, msg: `[${event.type}] ${event.message}` })),
    [recentEvents],
  );
  const previewCountTags = (values: Record<string, number> | undefined, prefix: string) =>
    Object.entries(values || {}).map(([key, value]) => (
      <Tag key={`${prefix}-${key}`} color="blue">
        {key} {value}
      </Tag>
    ));

  return (
    <PageContainer
      title="后台任务"
      subTitle="扫描、刮削和实时运行状态"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={refreshAll} loading={tasksLoading}>
            刷新
          </Button>
          <Button onClick={() => history.push('/settings/scrape-schedules')}>刮削计划</Button>
        </Space>
      }
    >
      <Card
        title="媒体扫描"
        style={{ marginBottom: 16 }}
        extra={
          <Space wrap>
            <Select
              placeholder="选择媒体库"
              style={{ width: 220 }}
              options={libraryOptions}
              value={selectedScanLibraryId}
              onChange={setSelectedScanLibraryId}
              showSearch
              optionFilterProp="label"
            />
            <Button
              type="primary"
              icon={<SyncOutlined />}
              disabled={!access.canScanLibrary}
              onClick={handleStartScan}
            >
              开始扫描
            </Button>
          </Space>
        }
      >
        <Table<ScanProgress>
          rowKey="libraryId"
          size="small"
          pagination={false}
          columns={scanColumns}
          dataSource={scanEntries}
          locale={{ emptyText: '当前没有进行中的扫描' }}
        />
      </Card>

      <Card
        title="启动刮削"
        style={{ marginBottom: 16 }}
        extra={
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={startingScrape}
            disabled={Boolean(preview && preview.totalItems <= 0)}
            onClick={handleStartScrape}
          >
            创建任务
          </Button>
        }
      >
        <Form<ScrapeLaunchValues> form={scrapeForm} layout="inline">
          <Form.Item name="scope" label="范围" rules={[{ required: true }]}>
            <Select
              style={{ width: 150 }}
              options={[
                { label: '全部', value: 'GLOBAL' },
                { label: '指定媒体库', value: 'LIBRARY' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate>
            {() =>
              scrapeForm.getFieldValue('scope') === 'LIBRARY' ? (
                <Form.Item name="libraryId" label="媒体库" rules={[{ required: true }]}>
                  <Select
                    style={{ width: 220 }}
                    options={libraryOptions}
                    showSearch
                    optionFilterProp="label"
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="targetStatus" label="目标" rules={[{ required: true }]}>
            <Select style={{ width: 130 }} options={TARGET_STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item name="mediaTypes" label="类型">
            <Select mode="multiple" style={{ width: 260 }} options={MEDIA_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="batchSize" label="批量">
            <InputNumber min={1} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="requestDelayMs" label="间隔 ms">
            <InputNumber min={0} step={500} style={{ width: 130 }} />
          </Form.Item>
        </Form>
        <Alert
          type={preview?.totalItems && preview.totalItems > 0 ? 'success' : 'warning'}
          showIcon
          style={{ marginTop: 16 }}
          message={
            previewLoading
              ? '正在计算候选媒体...'
              : `候选媒体 ${preview?.totalItems ?? 0} 项`
          }
          description={
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Space size={[8, 8]} wrap>
                <Tag color="processing">可见媒体 {preview?.allVisibleItems ?? 0}</Tag>
                <Tag color={preview?.enabledScrapers?.length ? 'purple' : 'warning'}>
                  刮削器 {preview?.enabledScrapers?.join(', ') || '未启用'}
                </Tag>
                {previewCountTags(preview?.byStatus, 'status')}
                {previewCountTags(preview?.byType, 'type')}
              </Space>
              {preview?.tips?.length ? (
                <Space direction="vertical" size={2}>
                  {preview.tips.map((tip) => (
                    <Typography.Text key={tip} type="secondary">
                      {tip}
                    </Typography.Text>
                  ))}
                </Space>
              ) : null}
            </Space>
          }
        />
      </Card>

      <Card
        title="刮削任务"
        style={{ marginBottom: 16 }}
        extra={
          <Button icon={<CloudSyncOutlined />} onClick={loadScrapeTasks} loading={tasksLoading}>
            刷新任务
          </Button>
        }
      >
        <Table<ScrapeTaskResponse>
          rowKey="id"
          size="small"
          loading={tasksLoading}
          dataSource={scrapeTasks}
          columns={taskColumns}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Card title="实时事件">
        <List
          bordered
          dataSource={displayEvents}
          locale={{ emptyText: '等待任务事件' }}
          renderItem={(item) => (
            <List.Item>
              <Typography.Text mark>[{item.time}]</Typography.Text> {item.msg}
            </List.Item>
          )}
        />
      </Card>

      <Drawer
        title={detailTask ? `刮削任务 #${detailTask.id}` : '任务详情'}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={520}
      >
        {detailTask && (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="状态">{detailTask.status}</Descriptions.Item>
            <Descriptions.Item label="范围">{detailTask.libraryName || '全部可见媒体库'}</Descriptions.Item>
            <Descriptions.Item label="目标状态">{detailTask.targetStatus}</Descriptions.Item>
            <Descriptions.Item label="媒体类型">
              <Space size={[4, 4]} wrap>
                {parseMediaTypes(detailTask.mediaTypes).length > 0
                  ? parseMediaTypes(detailTask.mediaTypes).map((type) => <Tag key={type}>{type}</Tag>)
                  : '-'}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="进度">
              成功 {detailTask.scrapedItems}/{detailTask.totalItems}，错误 {detailTask.errorItems}
            </Descriptions.Item>
            <Descriptions.Item label="参数">
              批量 {detailTask.batchSize ?? '-'}，间隔 {detailTask.requestDelayMs ?? '-'}ms
            </Descriptions.Item>
            <Descriptions.Item label="触发">{detailTask.triggerType}</Descriptions.Item>
            <Descriptions.Item label="开始">{formatIso(detailTask.startedAt)}</Descriptions.Item>
            <Descriptions.Item label="结束">{formatIso(detailTask.finishedAt)}</Descriptions.Item>
            {detailTask.errorLog && (
              <Descriptions.Item label="错误日志">
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                  {detailTask.errorLog}
                </Typography.Paragraph>
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Drawer>

      <LibraryScanModal
        open={scanModalOpen}
        libraryId={selectedScanLibraryId}
        libraryName={libraries.find((lib) => lib.id === selectedScanLibraryId)?.name}
        onClose={() => setScanModalOpen(false)}
        onSuccess={() => fetchScanSnapshot?.()}
      />
    </PageContainer>
  );
};

export default Tasks;
