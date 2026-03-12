import { PageContainer, ProTable, ProColumns } from '@ant-design/pro-components';
import { useParams, history } from '@umijs/max';
import React, { useRef } from 'react';
import { getItems } from '@/services/media';

const LibraryDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const actionRef = useRef<any>();

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
    <PageContainer title={`媒体库 ${id} 内容`}>
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
