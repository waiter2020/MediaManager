import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  List,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { history, useModel } from '@umijs/max';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { LoadingOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  cancelScrapeTask,
  getScrapeTask,
  listScrapeTasks,
  startScrapeAll,
  startScrapeLibrary,
  type ScrapeTaskResponse,
} from '@/services/scrape';
import { getLibraries } from '@/services/library';
import type { MediaLibrary } from '@/types/library';
import type { ScanProgress } from '@/models/global';

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
};

const Tasks: React.FC = () => {
  const { scanStatus, recentEvents } = useModel('global');
  const [scrapeTasks, setScrapeTasks] = useState<ScrapeTaskResponse[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [startingScrape, setStartingScrape] = useState(false);
  const [libraries, setLibraries] = useState<Pick<MediaLibrary, 'id' | 'name'>[]>([]);
  const [selectedLibraryId, setSelectedLibraryId] = useState<number | undefined>();
  const [detailTask, setDetailTask] = useState<ScrapeTaskResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

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
      setLibraries((res.data || []).map((lib) => ({ id: lib.id, name: lib.name })));
    }
  }, []);

  useEffect(() => {
    loadScrapeTasks();
    loadLibraries();
  }, [loadScrapeTasks, loadLibraries]);

  useEffect(() => {
    loadScrapeTasks();
  }, [loadScrapeTasks, recentEvents?.length]);

  const handleStartScrape = async () => {
    setStartingScrape(true);
    try {
      const res = await startScrapeAll('UNIDENTIFIED');
      if (res.code === 200) {
        if (res.data?.id) {
          message.success(`全库刮削任务 #${res.data.id} 已创建`);
        } else {
          message.info('当前没有可刮削的媒体项');
        }
        loadScrapeTasks();
      }
    } finally {
      setStartingScrape(false);
    }
  };

  const handleStartLibraryScrape = async () => {
    if (!selectedLibraryId) {
      message.warning('请选择媒体库');
      return;
    }
    setStartingScrape(true);
    try {
      const res = await startScrapeLibrary(selectedLibraryId, 'UNIDENTIFIED');
      if (res.code === 200) {
        message.success(res.data ? `刮削任务 #${res.data.id} 已创建` : '无待刮削条目');
        loadScrapeTasks();
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

  const displayEvents = useMemo(
    () => (recentEvents || []).map((event) => ({ time: event.time, msg: `[${event.type}] ${event.message}` })),
    [recentEvents],
  );

  const scanEntries = Object.values(scanStatus || {}) as ScanProgress[];

  const taskColumns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '库', dataIndex: 'libraryName', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => <Tag color={STATUS_COLOR[status] || 'default'}>{status}</Tag>,
    },
    {
      title: '进度',
      width: 180,
      render: (_: unknown, row: ScrapeTaskResponse) => {
        const percent = row.totalItems && row.totalItems > 0
          ? Math.round((row.scrapedItems / row.totalItems) * 100)
          : 0;
        let progressStatus: 'active' | 'exception' | 'normal' | 'success' = 'normal';
        if (row.status === 'RUNNING') progressStatus = 'active';
        else if (row.status === 'FAILED') progressStatus = 'exception';
        else if (row.status === 'SUCCESS') progressStatus = 'success';
        
        return (
          <div style={{ width: 140 }}>
            <Progress percent={percent} size="small" status={progressStatus} />
            <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 2 }}>
              {row.scrapedItems ?? 0}/{row.totalItems ?? 0} {row.errorItems && row.errorItems > 0 ? `(失败 ${row.errorItems})` : ''}
            </div>
          </div>
        );
      },
    },
    { title: '触发', dataIndex: 'triggerType', width: 90 },
    {
      title: '错误',
      dataIndex: 'errorLog',
      ellipsis: true,
      render: (value: string, row: ScrapeTaskResponse) =>
        row.status === 'FAILED' && value ? (
          <Typography.Text type="danger" ellipsis={{ tooltip: value }}>
            {value}
          </Typography.Text>
        ) : (
          '-'
        ),
    },
    {
      title: '开始',
      dataIndex: 'startedAt',
      width: 170,
      render: (value: string) => (value ? new Date(value).toLocaleString() : '-'),
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, row: ScrapeTaskResponse) => (
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

  return (
    <PageContainer
      title="后台任务"
      subTitle="库扫描运行本地提取器，刮削任务运行远程元数据插件"
      extra={<Button onClick={() => history.push('/settings/scrape-schedules')}>刮削计划</Button>}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="扫描与刮削职责分离"
        description="扫描负责发现文件并执行 NFO、FFprobe、EXIF 等本地提取器；远程元数据请使用刮削任务、计划任务或媒体详情页的手动识别。"
      />

      <Card title="正在扫描的媒体库" style={{ marginBottom: 16 }}>
        {scanEntries.length === 0 ? (
          <Typography.Text type="secondary">当前没有进行中的库扫描</Typography.Text>
        ) : (
          <List
            dataSource={scanEntries}
            renderItem={(scan) => {
              const percent = scan.totalFiles && scan.totalFiles > 0
                ? Math.round((scan.scannedFiles / scan.totalFiles) * 100)
                : 0;
              return (
                <List.Item style={{ display: 'block', padding: '16px 0' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, alignItems: 'center' }}>
                    <Typography.Text strong style={{ fontSize: 14 }}>{scan.libraryName}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      已扫描 {scan.scannedFiles} / 共 {scan.totalFiles} 文件
                    </Typography.Text>
                  </div>
                  <Progress percent={percent} status="active" strokeColor={{ '0%': '#108ee9', '100%': '#87d068' }} />
                  <div style={{ fontSize: 11, color: 'var(--color-text-tertiary)', marginTop: 6, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                    当前路径: {scan.currentPath}
                  </div>
                </List.Item>
              );
            }}
          />
        )}
      </Card>

      <Card
        title="刮削任务"
        style={{ marginBottom: 16 }}
        extra={
          <Space wrap>
            <Select
              allowClear
              placeholder="选择媒体库"
              style={{ width: 180 }}
              options={libraries.map((lib) => ({ label: lib.name, value: lib.id }))}
              value={selectedLibraryId}
              onChange={setSelectedLibraryId}
            />
            <Button loading={startingScrape} onClick={handleStartLibraryScrape} disabled={!selectedLibraryId}>
              库内刮削
            </Button>
            <Button loading={startingScrape} onClick={handleStartScrape}>
              启动全库刮削
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadScrapeTasks} loading={tasksLoading}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          size="small"
          loading={tasksLoading}
          dataSource={scrapeTasks}
          columns={taskColumns}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Card title="实时事件">
        <Typography.Paragraph type="secondary">
          扫描与刮削的 SSE 通知；全局模型中保留 {recentEvents?.length ?? 0} 条近期事件。
        </Typography.Paragraph>
        <List
          bordered
          dataSource={displayEvents}
          locale={{ emptyText: <Space><LoadingOutlined /> 等待任务执行...</Space> }}
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
        width={480}
      >
        {detailTask && (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="状态">{detailTask.status}</Descriptions.Item>
            <Descriptions.Item label="媒体库">{detailTask.libraryName || '全部'}</Descriptions.Item>
            <Descriptions.Item label="目标状态">{detailTask.targetStatus}</Descriptions.Item>
            <Descriptions.Item label="进度">
              {detailTask.scrapedItems}/{detailTask.totalItems}（错误 {detailTask.errorItems}）
            </Descriptions.Item>
            <Descriptions.Item label="触发">{detailTask.triggerType}</Descriptions.Item>
            <Descriptions.Item label="开始">
              {detailTask.startedAt ? new Date(detailTask.startedAt).toLocaleString() : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="结束">
              {detailTask.finishedAt ? new Date(detailTask.finishedAt).toLocaleString() : '-'}
            </Descriptions.Item>
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
    </PageContainer>
  );
};

export default Tasks;
