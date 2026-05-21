import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Table, message, Tag } from 'antd';
import { history, Link } from '@umijs/max';
import { listAiSuggestions, approveSuggestion, rejectSuggestion } from '@/services/ai';

const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  overview: '简介',
};

function fieldLabel(fieldName: string): string {
  if (!fieldName) return '-';
  if (fieldName.startsWith('tag:')) {
    const tagName = fieldName.slice(4);
    return tagName ? `标签 · ${tagName}` : '标签';
  }
  return FIELD_LABELS[fieldName] || fieldName;
}

const IntelligenceReview: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await listAiSuggestions();
      if (res.code === 200) {
        setData(res.data || []);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const columns = [
    {
      title: '媒体',
      dataIndex: 'mediaTitle',
      width: 200,
      ellipsis: true,
      render: (title: string, row: any) => (
        <Link to={`/media/${row.mediaItemId}`}>{title || `#${row.mediaItemId}`}</Link>
      ),
    },
    {
      title: '字段',
      dataIndex: 'fieldName',
      width: 140,
      render: (name: string) => <Tag>{fieldLabel(name)}</Tag>,
    },
    { title: '建议值', dataIndex: 'suggestedValue', ellipsis: true },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 90,
      render: (v: number) => `${Math.round((v ?? 0) * 100)}%`,
    },
    { title: '来源', dataIndex: 'providerId', width: 100 },
    {
      title: '操作',
      width: 220,
      render: (_: unknown, row: any) => (
        <>
          <Button type="link" onClick={() => history.push(`/media/${row.mediaItemId}`)}>
            查看
          </Button>
          <Button
            type="link"
            onClick={async () => {
              const res = await approveSuggestion(row.id);
              if (res.code === 200) {
                message.success('已批准');
                load();
              }
            }}
          >
            批准
          </Button>
          <Button
            type="link"
            danger
            onClick={async () => {
              const res = await rejectSuggestion(row.id);
              if (res.code === 200) {
                message.success('已拒绝');
                load();
              }
            }}
          >
            拒绝
          </Button>
        </>
      ),
    },
  ];

  return (
    <PageContainer title="AI 建议审核">
      <Table rowKey="id" loading={loading} dataSource={data} columns={columns} />
    </PageContainer>
  );
};

export default IntelligenceReview;
