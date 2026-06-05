import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Space, Tag, Typography, message } from 'antd';
import { history, useAccess } from '@umijs/max';
import { formatEmbeddingDimensions, getAiHealth, type AiHealth } from '@/services/ai';
import { getSystemCapabilities, type SystemCapabilities } from '@/services/system';
import { getSettingsSummary, type GeneralSettings as GeneralSettingsSummary } from '@/services/settings';
import { runReindexWithPolling, type ReindexStatus } from '@/utils/reindexPoll';

const GeneralSettings: React.FC = () => {
  const access = useAccess();
  const [summary, setSummary] = useState<GeneralSettingsSummary | null>(null);
  const [capabilities, setCapabilities] = useState<SystemCapabilities | null>(null);
  const [aiHealth, setAiHealth] = useState<AiHealth | null>(null);
  const [aiHealthLoading, setAiHealthLoading] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [reindexProgress, setReindexProgress] = useState<ReindexStatus | null>(null);

  const runAiHealthCheck = async (refresh = false) => {
    setAiHealthLoading(true);
    setAiHealth(null);
    try {
      const res = await getAiHealth({ refresh });
      if (res.code === 200) {
        setAiHealth(res.data || null);
      } else {
        message.error(res.message || '检测失败');
      }
    } catch {
      message.error('AI 健康检查失败');
    } finally {
      setAiHealthLoading(false);
    }
  };

  const runReindex = async () => {
    setReindexing(true);
    setReindexProgress(null);
    try {
      const finalStatus = await runReindexWithPolling(setReindexProgress);
      if (finalStatus.state === 'done') {
        message.success(
          `索引已重建：FTS ${finalStatus.ftsIndexed ?? 0} 条，向量 ${finalStatus.embedIndexed ?? 0} 条`,
        );
      } else {
        message.error(finalStatus.message || '重建索引失败');
      }
    } catch (error) {
      const err = error as { message?: string };
      message.error(err.message || '重建索引失败');
    } finally {
      setReindexing(false);
    }
  };

  useEffect(() => {
    if (!access.canManageSystem) return;

    getSettingsSummary().then((res) => {
      if (res.code === 200) setSummary(res.data || null);
    });
    getSystemCapabilities().then((res) => {
      if (res.code === 200) setCapabilities(res.data || null);
    });
    runAiHealthCheck();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [access.canManageSystem]);

  if (!access.canManageSystem) {
    return (
      <PageContainer title="设置概览">
        <Alert type="info" message="你没有系统管理权限，请从左侧选择其他设置项。" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="设置概览" content="查看系统版本、运行时能力，并维护 AI 与搜索索引。">
      <Card title="系统信息" style={{ marginBottom: 16 }}>
        <Space direction="vertical">
          <Typography.Text>版本：{summary?.version ?? '-'}</Typography.Text>
          <Typography.Text>初始化：{summary?.setupCompleted ? '已完成' : '未完成'}</Typography.Text>
        </Space>
      </Card>

      {capabilities ? (
        <Card title="运行时能力" style={{ marginBottom: 16 }}>
          <Space wrap>
            <Tag color={capabilities.ffmpegAvailable ? 'success' : 'default'}>
              FFmpeg {capabilities.ffmpegAvailable ? '可用' : '不可用'}
            </Tag>
            <Typography.Text type="secondary">路径：{capabilities.ffmpegPath || '-'}</Typography.Text>
          </Space>
        </Card>
      ) : null}

      <Card title="AI 服务状态" style={{ marginBottom: 16 }} loading={aiHealthLoading}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            提供方、模型与 API Key 请在 AI 设置中配置。
          </Typography.Paragraph>
          <Space wrap>
            <Button type="primary" onClick={() => history.push('/settings/ai')}>
              打开 AI 设置
            </Button>
            <Button loading={aiHealthLoading} onClick={() => runAiHealthCheck(true)}>
              重新检测
            </Button>
            <Button loading={reindexing} onClick={runReindex}>
              重建搜索索引
            </Button>
          </Space>
          {reindexProgress?.state === 'running' ? (
            <Typography.Text type="secondary">{reindexProgress.message || '重建中...'}</Typography.Text>
          ) : null}
          {aiHealth ? (
            <Alert
              type={aiHealth.status === 'ok' ? 'success' : 'warning'}
              showIcon
              message={
                <>
                  <Tag color={aiHealth.status === 'ok' ? 'success' : 'warning'}>
                    {aiHealth.status === 'ok' ? '正常' : '降级'}
                  </Tag>
                  <Typography.Text style={{ marginLeft: 8 }}>
                    提供商：{aiHealth.provider || '-'} - 向量维度：
                    {formatEmbeddingDimensions(aiHealth.embeddingDimensions)}
                  </Typography.Text>
                </>
              }
            />
          ) : !aiHealthLoading ? (
            <Typography.Text type="secondary">无法获取 AI 健康状态</Typography.Text>
          ) : null}
        </Space>
      </Card>
    </PageContainer>
  );
};

export default GeneralSettings;
