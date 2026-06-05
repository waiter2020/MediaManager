import { PageContainer } from '@ant-design/pro-components';
import { Badge, Card, Input, Select, Space, Switch, Tabs, Button, message } from 'antd';
import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { getAccessToken } from '@/utils/authSession';

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

const TerminalConsole: React.FC<{
  logs: SystemLogEvent[];
  autoScroll: boolean;
  clearLogs: () => void;
}> = ({ logs, autoScroll, clearLogs }) => {
  const consoleRef = useRef<HTMLDivElement | null>(null);
  const lockedScrollTopRef = useRef(0);

  useLayoutEffect(() => {
    const el = consoleRef.current;
    if (!el) return;

    if (autoScroll) {
      el.scrollTop = el.scrollHeight;
      lockedScrollTopRef.current = el.scrollTop;
      return;
    }

    const maxScrollTop = Math.max(0, el.scrollHeight - el.clientHeight);
    el.scrollTop = Math.min(lockedScrollTopRef.current, maxScrollTop);
  }, [logs, autoScroll]);

  const handleScroll = () => {
    if (consoleRef.current) {
      lockedScrollTopRef.current = consoleRef.current.scrollTop;
    }
  };

  return (
    <div className="terminal-container" style={{ position: 'relative', marginTop: 12 }}>
      <div ref={consoleRef} className="terminal-console" onScroll={handleScroll}>
        {logs.length === 0 ? (
          <div style={{ color: '#6b7280', textAlign: 'center', padding: '60px 0', fontFamily: 'monospace' }}>
            _ 终端空闲中，等待日志输入...
          </div>
        ) : (
          logs.map((item, index) => {
            const timeLabel = new Date(item.timestamp || Date.now()).toLocaleTimeString();
            let levelClass = 'terminal-level-info';
            if (item.level === 'DEBUG' || item.level === 'TRACE') levelClass = 'terminal-level-debug';
            else if (item.level === 'WARN') levelClass = 'terminal-level-warn';
            else if (item.level === 'ERROR') levelClass = 'terminal-level-error';

            return (
              <div className="terminal-line" key={index}>
                <span className="terminal-time">[{timeLabel}]</span>
                <span className={levelClass}>[{item.level}]</span>
                {item.source && <span className="terminal-source">[{item.source}]</span>}
                <span className="terminal-logger">[{item.logger}]</span>
                <span className="terminal-message">{item.message}</span>
                {item.exceptionShort && (
                  <span className="terminal-exception">{item.exceptionShort}</span>
                )}
              </div>
            );
          })
        )}
      </div>
      <Button
        size="small"
        style={{
          position: 'absolute',
          top: 10,
          right: 15,
          background: 'rgba(255, 255, 255, 0.08)',
          border: '1px solid rgba(255, 255, 255, 0.15)',
          color: '#d1d5db',
          zIndex: 10,
          borderRadius: '6px',
        }}
        onClick={clearLogs}
      >
        清除控制台
      </Button>
    </div>
  );
};

const LogsPage: React.FC = () => {
  const [logs, setLogs] = useState<SystemLogEvent[]>([]);
  const [autoScroll, setAutoScroll] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [useRegex, setUseRegex] = useState(false);
  const [levelFilter, setLevelFilter] = useState<string | undefined>(undefined);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const token = getAccessToken();
    const url = token
      ? `/api/v1/system/logs/stream?token=${encodeURIComponent(token)}`
      : '/api/v1/system/logs/stream';
    const es = new EventSource(url);

    const handleLogEvent = (e: Event) => {
      try {
        const data: SystemLogEvent = JSON.parse((e as MessageEvent<string>).data);
        setLogs((prev) => [...prev, data].slice(-MAX_LOGS));
      } catch {
        // ignore malformed payloads
      }
    };

    es.addEventListener('log', handleLogEvent);

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

  const matchesFilters = (log: SystemLogEvent) => {
    if (levelFilter && log.level !== levelFilter) return false;
    if (keyword) {
      if (useRegex) {
        try {
          const regex = new RegExp(keyword, 'i');
          return regex.test(`${log.message} ${log.logger} ${log.exceptionShort || ''}`);
        } catch {
          return `${log.message} ${log.logger} ${log.exceptionShort || ''}`.toLowerCase().includes(keyword.toLowerCase());
        }
      } else {
        return `${log.message} ${log.logger} ${log.exceptionShort || ''}`.toLowerCase().includes(keyword.toLowerCase());
      }
    }
    return true;
  };

  const filteredAppLogs = useMemo(
    () => logs.filter((log) => log.source !== 'TASK' && matchesFilters(log)),
    [logs, levelFilter, keyword, useRegex],
  );

  const filteredTaskLogs = useMemo(
    () => logs.filter((log) => log.source === 'TASK' && matchesFilters(log)),
    [logs, levelFilter, keyword, useRegex],
  );

  const clearLogs = () => {
    setLogs([]);
    message.success('终端已清空');
  };

  return (
    <PageContainer>
      <style>{`
        .terminal-console {
          background: #080810 !important;
          border: 1px solid rgba(255, 255, 255, 0.08) !important;
          border-radius: 12px;
          box-shadow: inset 0 2px 10px rgba(0, 0, 0, 0.9), 0 8px 30px rgba(0, 0, 0, 0.6);
          font-family: 'Fira Code', 'Consolas', 'Courier New', monospace;
          padding: 16px;
          overflow-y: auto;
          min-height: 480px;
          max-height: 520px;
          font-size: 13px;
          line-height: 1.6;
          color: #d1d5db;
        }

        .terminal-line {
          margin-bottom: 6px;
          white-space: pre-wrap;
          word-break: break-all;
          border-left: 2px solid transparent;
          padding-left: 8px;
          transition: background 0.2s ease;
        }

        .terminal-line:hover {
          background: rgba(255, 255, 255, 0.03);
        }

        .terminal-time {
          color: #6b7280;
          margin-right: 8px;
          user-select: none;
        }

        .terminal-level-debug {
          color: #9ca3af;
          font-weight: bold;
          margin-right: 8px;
        }

        .terminal-level-info {
          color: #38bdf8;
          font-weight: bold;
          margin-right: 8px;
        }

        .terminal-level-warn {
          color: #fbbf24;
          font-weight: bold;
          margin-right: 8px;
        }

        .terminal-level-error {
          color: #f87171;
          font-weight: bold;
          margin-right: 8px;
        }

        .terminal-source {
          color: #c084fc;
          margin-right: 8px;
        }

        .terminal-logger {
          color: #34d399;
          opacity: 0.85;
          margin-right: 8px;
        }

        .terminal-message {
          color: #e5e7eb;
        }

        .terminal-exception {
          color: #fca5a5;
          background: rgba(239, 68, 68, 0.1);
          padding: 4px 8px;
          border-radius: 4px;
          display: block;
          margin-top: 4px;
          font-size: 12px;
          border: 1px solid rgba(239, 68, 68, 0.2);
        }

        @media (max-width: 640px) {
          .terminal-console {
            min-height: 340px;
            max-height: 56vh;
            padding: 12px;
            font-size: 12px;
          }

          .terminal-container > .ant-btn {
            position: static !important;
            width: 100%;
            margin-top: 8px;
          }
        }
      `}</style>

      <Card
        title={
          <Space>
            <span>系统日志</span>
            <Badge status={connected ? 'success' : 'error'} text={connected ? '实时连接中' : '连接已断开'} />
          </Space>
        }
        extra={
          <Space wrap>
            <Select
              allowClear
              placeholder="级别"
              style={{ width: 100 }}
              value={levelFilter}
              onChange={(v) => setLevelFilter(v)}
              options={[
                { label: 'INFO', value: 'INFO' },
                { label: 'WARN', value: 'WARN' },
                { label: 'ERROR', value: 'ERROR' },
                { label: 'DEBUG', value: 'DEBUG' },
              ]}
            />
            <Input.Search
              allowClear
              placeholder={useRegex ? "正则表达式搜索..." : "搜索消息 / logger..."}
              onSearch={setKeyword}
              onChange={(e) => setKeyword(e.target.value)}
              style={{ width: 260 }}
            />
            <Space>
              <span style={{ color: 'rgba(255,255,255,0.65)', fontSize: 13 }}>正则匹配</span>
              <Switch checked={useRegex} onChange={setUseRegex} size="small" />
            </Space>
            <Space style={{ marginLeft: 8 }}>
              <span style={{ color: 'rgba(255,255,255,0.65)', fontSize: 13 }}>自动滚动</span>
              <Switch checked={autoScroll} onChange={setAutoScroll} size="small" />
            </Space>
          </Space>
        }
        bodyStyle={{ padding: '12px 24px 24px' }}
      >
        <Tabs
          items={[
            {
              key: 'app',
              label: `应用日志 (${filteredAppLogs.length})`,
              children: (
                <TerminalConsole
                  logs={filteredAppLogs}
                  autoScroll={autoScroll}
                  clearLogs={clearLogs}
                />
              ),
            },
            {
              key: 'task',
              label: `后台任务 (${filteredTaskLogs.length})`,
              children: (
                <TerminalConsole
                  logs={filteredTaskLogs}
                  autoScroll={autoScroll}
                  clearLogs={clearLogs}
                />
              ),
            },
          ]}
        />
      </Card>
    </PageContainer>
  );
};

export default LogsPage;
