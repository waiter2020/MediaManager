import { PageContainer, ProTable, ProColumns } from '@ant-design/pro-components';
import { useParams, history } from '@umijs/max';
import React, { useEffect, useRef, useState } from 'react';
import { Button, Card, Space, Statistic, message } from 'antd';
import { getItems } from '@/services/media';
import { getLibraryStats, triggerScan } from '@/services/library';

const LibraryDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const actionRef = useRef<any>();
  const [stats, setStats] = useState<any>(null);
  const [scanning, setScanning] = useState(false);

  useEffect(() => {
    if (id) {
      getLibraryStats(Number(id)).then((res) => {
        if (res.code === 200) setStats(res.data);
      });
    }
  }, [id]);

  const handleScan = async () => {
    setScanning(true);
    try {
      const res = await triggerScan(Number(id));
      if (res.code === 200) {
        message.success('扫描已启动');
      }
    } finally {
      setScanning(false);
    }
  };

  const columns: ProColumns<any>[] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { 
      title: '标题', 
      dataIndex: 'title',
      render: (dom, entity) => <a onClick={() => history.push(`/media/${entity.id}`)}>{dom}</a>
    },
    { title: '类型', dataIndex: 'type', width: 100 },
    { title: '状态', dataIndex: 'status', width: 120 },
    { title: '发行日期', dataIndex: 'releaseDate', valueType: 'date', width: 120 },
  ];

  return (
    <PageContainer
      title={stats?.libraryName ? `${stats.libraryName}` : `媒体库 ${id}`}
      extra={
        <Space>
          <Button onClick={() => history.push(`/libraries/${id}/plugins`)}>插件配置</Button>
          <Button onClick={() => history.push(`/libraries/${id}/edit`)}>编辑</Button>
          <Button type="primary" loading={scanning} onClick={handleScan}>
            扫描库
          </Button>
        </Space>
      }
    >
      {stats && (
        <Card style={{ marginBottom: 16 }}>
          <Space size="large">
            <Statistic title="可见媒体项" value={stats.totalItems ?? 0} />
            <Statistic title="类型" value={stats.libraryType ?? '-'} />
          </Space>
        </Card>
      )}
      <ProTable
        actionRef={actionRef}
        columns={columns}
        rowKey="id"
        request={async (params) => {
          const res = await getItems({
            libraryId: Number(id),
            page: params.current,
            size: params.pageSize,
          });
          return {
            data: res?.data?.items || [],
            success: res?.code === 200,
            total: res?.data?.total || 0,
          };
        }}
        search={false}
      />
    </PageContainer>
  );
};

export default LibraryDetail;
