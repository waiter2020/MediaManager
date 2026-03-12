import { PageContainer } from '@ant-design/pro-components';
import { Card, List, Spin, Typography, Space } from 'antd';
import { useModel } from '@umijs/max';
import React, { useEffect, useState } from 'react';
import { LoadingOutlined } from '@ant-design/icons';

const Tasks: React.FC = () => {
  const [logs, setLogs] = useState<{time: string, msg: string}[]>([]);

  // Hook into SSE events directly via standard EventSource or a shared state from the global model
  // For simplicity since the global model shows toast, we'll establish a secondary listener here
  // strictly for displaying history in the page.
  
  useEffect(() => {
      const clientId = 'task-view-' + Math.random().toString(36).substring(7);
      const eventSource = new EventSource(`/api/v1/sse/events?clientId=${clientId}`);
      
      const addLog = (msg: string) => {
          setLogs(prev => [{time: new Date().toLocaleTimeString(), msg}, ...prev].slice(0, 50));
      };

      eventSource.addEventListener('scan-start', (e: any) => addLog(`[START] ${e.data}`));
      eventSource.addEventListener('scan-progress', (e: any) => addLog(`[PROGRESS] ${e.data}`));
      eventSource.addEventListener('scan-end', (e: any) => addLog(`[END] ${e.data}`));

      return () => eventSource.close();
  }, []);

  return (
    <PageContainer>
      <Card title="后台任务与扫描状态">
        <Typography.Paragraph type="secondary">
          当前会话的后台扫描进度和通知将实时显示在下方。
        </Typography.Paragraph>

        <List
          header={<div>实时日志</div>}
          bordered
          dataSource={logs}
          renderItem={(item) => (
            <List.Item>
              <Typography.Text mark>[{item.time}]</Typography.Text> {item.msg}
            </List.Item>
          )}
          locale={{ emptyText: <Space><Spin indicator={<LoadingOutlined />} /> 等待任务执行...</Space> }}
        />
      </Card>
    </PageContainer>
  );
};

export default Tasks;
