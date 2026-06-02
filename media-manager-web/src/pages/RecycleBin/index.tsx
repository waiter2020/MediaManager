import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import { DeleteOutlined, UndoOutlined } from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import {
  listRecycleBin,
  purgeRecycleFile,
  restoreRecycleFile,
  type RecycleBinItem,
} from '@/services/recycle';

function formatBytes(bytes?: number) {
  if (!bytes || bytes <= 0) return '-';
  if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)} GB`;
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`;
  return `${(bytes / 1024).toFixed(1)} KB`;
}

const RecycleBinPage: React.FC = () => {
  const access = useAccess();
  const canMutate = access.canDeleteMedia;
  const [data, setData] = useState<RecycleBinItem[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await listRecycleBin();
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
    { title: 'ID', dataIndex: 'id', width: 60, render: (v: number) => <Typography.Text type="secondary">{v}</Typography.Text> },
    {
      title: '媒体',
      dataIndex: 'mediaTitle',
      width: 180,
      ellipsis: true,
      render: (title: string, row: RecycleBinItem) =>
        row.mediaItemId ? (
          <Link to={`/media/${row.mediaItemId}`} style={{ fontWeight: 500 }}>{title || `#${row.mediaItemId}`}</Link>
        ) : (
          <Typography.Text type="secondary">{title || '-'}</Typography.Text>
        ),
    },
    {
      title: '媒体库',
      dataIndex: 'libraryName',
      width: 110,
      render: (name: string) => name ? <Tag color="blue" style={{ borderRadius: 4 }}>{name}</Tag> : '-',
    },
    { title: '文件名', dataIndex: 'fileName', ellipsis: true },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 100,
      render: (v?: number) => <span style={{ fontFamily: 'monospace', opacity: 0.85 }}>{formatBytes(v)}</span>,
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      width: 160,
      render: (value: string) => (
        <span style={{ fontSize: 13, opacity: 0.7 }}>
          {value ? new Date(value).toLocaleString() : '-'}
        </span>
      ),
    },
    {
      title: '物理路径',
      dataIndex: 'filePath',
      ellipsis: true,
      render: (path: string) => (
        <Typography.Text type="secondary" code style={{ fontSize: 11, background: 'rgba(255,255,255,0.03)' }}>
          {path}
        </Typography.Text>
      ),
    },
    {
      title: '操作',
      width: 250,
      render: (_: unknown, row: RecycleBinItem) =>
        canMutate ? (
          <Space size="middle">
            <Button
              type="link"
              size="small"
              icon={<UndoOutlined />}
              onClick={async () => {
                const res = await restoreRecycleFile(row.id);
                if (res.code === 200) {
                  message.success('已从回收站成功恢复该媒体项！');
                  load();
                }
              }}
            >
              恢复
            </Button>
            <Space size={0}>
              <Popconfirm
                title="永久删除记录？"
                description="注意：该操作仅清理系统数据库中关于此文件的历史删除记录，磁盘源文件将保持完好。"
                okText="清除记录"
                cancelText="取消"
                onConfirm={async () => {
                  const res = await purgeRecycleFile(row.id);
                  if (res.code === 200) {
                    message.success('已清除删除记录');
                    load();
                  }
                }}
              >
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除记录
                </Button>
              </Popconfirm>
              <Popconfirm
                title="永久删除记录并彻底粉碎磁盘源文件？"
                description="🚨 警告：磁盘源文件将被永久删除！该物理操作不可逆，请务必谨慎！"
                okText="彻底粉碎文件"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={async () => {
                  const res = await purgeRecycleFile(row.id, { deleteSource: true });
                  if (res.code === 200) {
                    message.success('已物理粉碎源文件并清理数据！');
                    load();
                  }
                }}
              >
                <Button type="link" size="small" danger style={{ fontWeight: 600 }} icon={<DeleteOutlined />}>
                  粉碎文件
                </Button>
              </Popconfirm>
            </Space>
          </Space>
        ) : (
          <span style={{ color: 'rgba(255,255,255,0.3)' }}>仅查看</span>
        ),
    },
  ];

  const tableCardStyle: React.CSSProperties = {
    background: 'rgba(20, 20, 32, 0.6)',
    backdropFilter: 'blur(12px)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    borderRadius: '12px',
    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.25)',
  };

  return (
    <PageContainer
      title="媒体回收站"
      subTitle="管理已软删除的媒体文件。您可以随时一键恢复，或者从数据库中永久清除记录、或彻底物理抹除磁盘文件。"
    >
      <Card style={tableCardStyle} bodyStyle={{ padding: 0 }}>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={columns}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          style={{ background: 'transparent' }}
        />
      </Card>
    </PageContainer>
  );
};

export default RecycleBinPage;
