import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Table, message, Popconfirm } from 'antd';
import { Link } from '@umijs/max';
import { listRecycleBin, restoreRecycleFile, purgeRecycleFile } from '@/services/recycle';

const RecycleBinPage: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
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
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '媒体',
      dataIndex: 'mediaTitle',
      ellipsis: true,
      render: (title: string, row: any) =>
        row.mediaItemId ? (
          <Link to={`/media/${row.mediaItemId}`}>{title || `#${row.mediaItemId}`}</Link>
        ) : (
          title || '-'
        ),
    },
    { title: '媒体库', dataIndex: 'libraryName', width: 120 },
    { title: '文件名', dataIndex: 'fileName', ellipsis: true },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 100,
      render: (bytes: number) => {
        if (!bytes || bytes <= 0) return '-';
        if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)} GB`;
        if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`;
        return `${(bytes / 1024).toFixed(1)} KB`;
      },
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    { title: '路径', dataIndex: 'filePath', ellipsis: true },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, row: any) => (
        <>
          <Button
            type="link"
            onClick={async () => {
              const res = await restoreRecycleFile(row.id);
              if (res.code === 200) {
                message.success('已恢复');
                load();
              }
            }}
          >
            恢复
          </Button>
          <Popconfirm
            title="永久删除此记录？"
            onConfirm={async () => {
              const res = await purgeRecycleFile(row.id);
              if (res.code === 200) {
                message.success('已删除');
                load();
              }
            }}
          >
            <Button type="link" danger>
              永久删除
            </Button>
          </Popconfirm>
        </>
      ),
    },
  ];

  return (
    <PageContainer title="回收站">
      <Table rowKey="id" loading={loading} dataSource={data} columns={columns} pagination={{ pageSize: 20 }} />
    </PageContainer>
  );
};

export default RecycleBinPage;
