import { PageContainer } from '@ant-design/pro-components';
import { Card, List, Tabs, Tag, Typography, Space, Switch, Input, Select, Badge } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';

type LogSource = 'APP' | 'TASK' | string;

interface SystemLogEvent {
  timestamp: number;
  level: string;
  source: LogSource;
  logger: string;
  message: string;
  thread?: string;
  exceptionShort?: string;
  type?: string;
  libraryId?: number;
  summary?: string;
}

const MAX_LOGS = 500;

const levelColorMap: Record<string, string> = {
  TRACE: 'default',
  DEBUG: 'default',
  INFO: 'processing',
  WARN: 'warning',
  ERROR: 'error',
};

const LogsPage: React.FC = () => {
  const [logs, setLogs] = useState<SystemLogEvent[]>([]);
  const [autoScroll, setAutoScroll] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [levelFilter, setLevelFilter] = useState<string | undefined>(undefined);
  const listRef = useRef<HTMLDivElement | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const token = localStorage.getItem('accessToken');
    const url = token
      ? `/api/v1/system/logs/stream?token=${encodeURIComponent(token)}`
      : '/api/v1/system/logs/stream';
    const es = new EventSource(url);

    const handleLogEvent = (e: MessageEvent) => {
      try {
        const data: SystemLogEvent = JSON.parse(e.data);
        setLogs((prev) => {
          const next = [data, ...prev];
          if (next.length > MAX_LOGS) {
            return next.slice(0, MAX_LOGS);
          }
          return next;
        });
      } catch {
        // ignore malformed payloads
      }
    };

    es.addEventListener('log', handleLogEvent as any);

    es.onopen = () => {
      if (!cancelled) {
        setConnected(true);
      }
    };

    es.onerror = () => {
      es.close();
      if (!cancelled) {
        setConnected(false);
      }
    };

    return () => {
      cancelled = true;
      es.close();
      setConnected(false);
    };
  }, []);

  useEffect(() => {
    if (!autoScroll || !listRef.current) return;
    const container = listRef.current;
    container.scrollTop = 0;
  }, [logs, autoScroll]);

  const filteredAppLogs = useMemo(() => {
    return logs.filter((log) => {
      if (log.source === 'TASK') return false;
      if (levelFilter && log.level !== levelFilter) return false;
      if (keyword && !`${log.message} ${log.logger} ${log.exceptionShort || ''}`.includes(keyword)) {
        return false;
      }
      return true;
    });
  }, [logs, levelFilter, keyword]);

  const filteredTaskLogs = useMemo(() => {
    return logs.filter((log) => {
      if (log.source !== 'TASK') return false;
      if (levelFilter && log.level !== levelFilter) return false;
      if (keyword && !`${log.message} ${log.logger} ${log.exceptionShort || ''}`.includes(keyword)) {
        return false;
      }
      return true;
    });
  }, [logs, levelFilter, keyword]);

  const renderLogItem = (item: SystemLogEvent) => {
    const timeLabel = new Date(item.timestamp || Date.now()).toLocaleTimeString();
    const color = levelColorMap[item.level] || 'default';

    return (
      <List.Item>
        <Space align="start" size="small" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space size="small" wrap>
            <Typography.Text type="secondary">[{timeLabel}]</Typography.Text>
            <Tag color={color}>{item.level}</Tag>
            {item.source && <Tag>{item.source}</Tag>}
            {item.type && <Tag>{item.type}</Tag>}
          </Space>
          <Space direction="vertical" style={{ flex: 1, marginLeft: 8 }}>
            <Typography.Text>{item.message}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {item.logger}
              {item.thread ? ` · ${item.thread}` : ''}
              {item.libraryId ? ` · Library #${item.libraryId}` : ''}
            </Typography.Text>
            {item.exceptionShort && (
              <Typography.Text type="danger" style={{ fontSize: 12 }}>
                {item.exceptionShort}
              </Typography.Text>
            )}
          </Space>
        </Space>
      </List.Item>
    );
  };

  return (
    <PageContainer>
      <Card
        title={
          <Space>
            <span>系统日志</span>
            <Badge status={connected ? 'success' : 'error'} text={connected ? '实时连接中' : '连接已断开'} />
          </Space>
        }
        extra={
          <Space>
            <Select
              allowClear
              placeholder="级别"
              style={{ width: 120 }}
              value={levelFilter}
              onChange={(v) => setLevelFilter(v)}
              options={[
                { label: 'INFO', value: 'INFO' },
                { label: 'WARN', value: 'WARN' },
                { label: 'ERROR', value: 'ERROR' },
              ]}
            />
            <Input.Search
              allowClear
              placeholder="搜索消息 / logger"
              onSearch={setKeyword}
              onChange={(e) => setKeyword(e.target.value)}
              style={{ width: 260 }}
            />
            <Space>
              <span style={{ color: 'rgba(255,255,255,0.65)' }}>自动滚动</span>
              <Switch checked={autoScroll} onChange={setAutoScroll} />
            </Space>
          </Space>
        }
      >
        <Typography.Paragraph type="secondary">
          展示系统运行日志与后台任务事件，便于实时监控 MediaManager 的状态。
        </Typography.Paragraph>

        <Tabs
          items={[
            {
              key: 'app',
              label: `应用日志 (${filteredAppLogs.length})`,
              children: (
                <div
                  ref={listRef}
                  style={{ maxHeight: 480, overflowY: 'auto', marginTop: 8 }}
                >
                  <List
                    size="small"
                    dataSource={filteredAppLogs}
                    renderItem={renderLogItem}
                    locale={{ emptyText: '暂无日志' }}
                  />
                </div>
              ),
            },
            {
              key: 'task',
              label: `后台任务 (${filteredTaskLogs.length})`,
              children: (
                <div
                  style={{ maxHeight: 480, overflowY: 'auto', marginTop: 8 }}
                >
                  <List
                    size="small"
                    dataSource={filteredTaskLogs}
                    renderItem={renderLogItem}
                    locale={{ emptyText: '暂无任务相关日志' }}
                  />
                </div>
              ),
            },
          ]}
        />
      </Card>
    </PageContainer>
  );
};

export default LogsPage;

