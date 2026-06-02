import React, { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Tag } from 'antd';
import { getSystemCapabilities } from '@/services/system';

interface Capabilities {
  ffmpegAvailable?: boolean;
  ffmpegPath?: string;
  embeddingCount?: number;
  hasIndexedVectors?: boolean;
  embeddingAvailable?: boolean;
  aiProvider?: string;
  aiProviderName?: string;
  embedModel?: string;
  llmModel?: string;
  aiBaseUrl?: string;
  classifierEnabled?: boolean;
  isNoopProvider?: boolean;
  aiDegraded?: boolean;
  aiDegradedReason?: string;
}

const SystemCapabilitiesCard: React.FC = () => {
  const [caps, setCaps] = useState<Capabilities | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getSystemCapabilities()
      .then((res) => {
        if (res.code === 200) setCaps(res.data);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <Card loading title="运行时能力" />;
  }
  if (!caps) {
    return null;
  }

  return (
    <Card title="运行时能力" style={{ marginBottom: 16 }}>
      {caps.aiDegraded && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={caps.aiDegradedReason || 'AI 服务处于降级模式'}
        />
      )}
      <Descriptions column={2} size="small">
        <Descriptions.Item label="FFmpeg">
          <Tag color={caps.ffmpegAvailable ? 'success' : 'error'}>
            {caps.ffmpegAvailable ? '可用' : '不可用'}
          </Tag>
          {caps.ffmpegPath && <span style={{ marginLeft: 8 }}>{caps.ffmpegPath}</span>}
        </Descriptions.Item>
        <Descriptions.Item label="AI 提供方">
          {caps.aiProviderName || caps.aiProvider || '-'}
          {caps.isNoopProvider && (
            <Tag color="warning" style={{ marginLeft: 8 }}>noop</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="嵌入模型">{caps.embedModel || '-'}</Descriptions.Item>
        <Descriptions.Item label="LLM 模型">{caps.llmModel || '-'}</Descriptions.Item>
        <Descriptions.Item label="向量索引">
          {caps.embeddingCount ?? 0} 条
          {caps.hasIndexedVectors ? '（已索引）' : '（未索引）'}
        </Descriptions.Item>
        <Descriptions.Item label="语义搜索">
          <Tag color={caps.embeddingAvailable ? 'success' : 'default'}>
            {caps.embeddingAvailable ? '可用' : '不可用'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="AI 打标">
          {caps.classifierEnabled ? '已启用' : '已关闭'}
        </Descriptions.Item>
        <Descriptions.Item label="服务地址">{caps.aiBaseUrl || '-'}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default SystemCapabilitiesCard;
