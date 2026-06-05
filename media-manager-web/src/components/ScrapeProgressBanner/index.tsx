import React from 'react';
import { Alert, Progress } from 'antd';
import { CloudSyncOutlined } from '@ant-design/icons';
import { useModel } from '@umijs/max';
import type { ScrapeTaskEvent } from '@/models/global';

const ACTIVE = new Set(['PENDING', 'RUNNING']);

interface ScrapeTaskProgress extends ScrapeTaskEvent {
  total?: number;
}

const ScrapeProgressBanner: React.FC = () => {
  const { scrapeTasks } = useModel('global');
  const tasks = Object.values(scrapeTasks || {}) as ScrapeTaskProgress[];
  const active = tasks.filter((task) => ACTIVE.has(task.status?.toUpperCase?.() ?? task.status));
  if (active.length === 0) return null;

  return (
    <div style={{ marginBottom: 12 }}>
      {active.map((task) => {
        const total = task.total != null ? Number(task.total) : (task.scraped ?? 0) + (task.errors ?? 0);
        const processed = (task.scraped ?? 0) + (task.errors ?? 0);
        const percent =
          processed > 0 && total > 0
            ? Math.min(100, Math.round((processed / total) * 100))
            : undefined;
        return (
          <Alert
            key={task.taskId}
            type="warning"
            showIcon
            icon={<CloudSyncOutlined spin />}
            style={{ marginBottom: 8 }}
            message={
              <span>
                正在刮削 <strong>任务 #{task.taskId}</strong>
                {task.status ? ` / ${task.status}` : ''}
              </span>
            }
            description={
              <Progress
                percent={percent}
                size="small"
                status="active"
                format={() => `成功 ${task.scraped ?? 0} / 错误 ${task.errors ?? 0}`}
              />
            }
          />
        );
      })}
    </div>
  );
};

export default ScrapeProgressBanner;
