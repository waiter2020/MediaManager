import { PageContainer } from '@ant-design/pro-components';
import { Card, List, Table, Tag, Typography, Space, Button, message, Popconfirm } from 'antd';
import { useModel } from '@umijs/max';
import React, { useCallback, useEffect, useState } from 'react';
import { LoadingOutlined, ReloadOutlined } from '@ant-design/icons';
import { cancelScrapeTask, createScrapeTask, listScrapeTasks, ScrapeTaskResponse } from '@/services/scrape';

const STATUS_COLOR: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
};

const Tasks: React.FC = () => {
  const { scanStatus, recentEvents } = useModel('global');
  const [logs, setLogs] = useState<{ time: string; msg: string }[]>([]);
  const [scrapeTasks, setScrapeTasks] = useState<ScrapeTaskResponse[]>([]);
  const [tasksLoading, setTasksLoading] = useState(false);
  const [startingScrape, setStartingScrape] = useState(false);

  const handleStartScrape = async () => {
    setStartingScrape(true);
    try {
      const res = await createScrapeTask({ targetStatus: 'UNIDENTIFIED' });
      if (res.code === 200) {
        message.success(res.data ? `刮削任务 #${res.data.id} 已创建` : '刮削任务已提交');
        loadScrapeTasks();
      }
    } finally {
      setStartingScrape(false);
    }
  };

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

  useEffect(() => {
    loadScrapeTasks();
  }, [loadScrapeTasks]);

  useEffect(() => {
    const clientId = 'task-view-' + Math.random().toString(36).substring(7);
    const token = localStorage.getItem('accessToken');
    const tokenQ = token ? `&token=${encodeURIComponent(token)}` : '';
    const eventSource = new EventSource(`/api/v1/sse/events?clientId=${clientId}${tokenQ}`);

    const addLog = (msg: string) => {
      setLogs((prev) => [{ time: new Date().toLocaleTimeString(), msg }, ...prev].slice(0, 50));
    };

    eventSource.addEventListener('scan-start', (e: MessageEvent) => addLog(`[SCAN START] ${e.data}`));
    eventSource.addEventListener('scan-progress', (e: MessageEvent) => addLog(`[SCAN] ${e.data}`));
    eventSource.addEventListener('scan-end', (e: MessageEvent) => addLog(`[SCAN END] ${e.data}`));
    eventSource.addEventListener('scrape.task.updated', () => {
      loadScrapeTasks();
    });
    eventSource.addEventListener('scrape-end', (e: MessageEvent) => {
      addLog(`[SCRAPE END] ${e.data}`);
      loadScrapeTasks();
    });

    return () => eventSource.close();
  }, [loadScrapeTasks]);

  const scanEntries = Object.values(scanStatus || {});

  const taskColumns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '库', dataIndex: 'libraryName', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={STATUS_COLOR[s] || 'default'}>{s}</Tag>,
    },
    {
      title: '进度',
      width: 120,
      render: (_: unknown, row: ScrapeTaskResponse) =>
        `${row.scrapedItems ?? 0}/${row.totalItems ?? 0}`,
    },
    { title: '触发', dataIndex: 'triggerType', width: 90 },
    {
      title: '错误',
      dataIndex: 'errorLog',
      ellipsis: true,
      render: (v: string, row: ScrapeTaskResponse) =>
        row.status === 'FAILED' && v ? (
          <Typography.Text type="danger" ellipsis={{ tooltip: v }}>
            {v}
          </Typography.Text>
        ) : (
          '-'
        ),
    },
    {
      title: '开始',
      dataIndex: 'startedAt',
      width: 170,
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: '操作',
      width: 100,
      render: (_: unknown, row: ScrapeTaskResponse) =>
        row.status === 'RUNNING' || row.status === 'PENDING' ? (
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
        ) : (
          '-'
        ),
    },
  ];

  return (
    <PageContainer>
      <Card title="正在扫描的媒体库" style={{ marginBottom: 16 }}>
        {scanEntries.length === 0 ? (
          <Typography.Text type="secondary">当前无进行中的库扫描</Typography.Text>
        ) : (
          <List
            dataSource={scanEntries}
            renderItem={(s: any) => (
              <List.Item>
                <Typography.Text strong>{s.libraryName}</Typography.Text>
                <Typography.Text type="secondary" style={{ marginLeft: 12 }}>
                  {s.scannedFiles}/{s.totalFiles} · {s.currentPath}
                </Typography.Text>
              </List.Item>
            )}
          />
        )}
      </Card>

      <Card
        title="刮削任务"
        style={{ marginBottom: 16 }}
        extra={
          <Space>
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
          扫描与刮削的 SSE 通知；全局模型另有 {recentEvents?.length ?? 0} 条近期事件。
        </Typography.Paragraph>
        <List
          bordered
          dataSource={logs}
          locale={{ emptyText: <Space><LoadingOutlined /> 等待任务执行...</Space> }}
          renderItem={(item) => (
            <List.Item>
              <Typography.Text mark>[{item.time}]</Typography.Text> {item.msg}
            </List.Item>
          )}
        />
      </Card>
    </PageContainer>
  );
};

export default Tasks;
