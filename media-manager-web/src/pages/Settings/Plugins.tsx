import React from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Typography } from 'antd';
import { history, useAccess } from '@umijs/max';
import { listPlugins, type PluginCatalogItem } from '@/services/plugin';
import { pluginKindLabel } from '@/utils/pluginLabels';

const PluginsSettingsPage: React.FC = () => {
  const access = useAccess();

  const columns: ProColumns<PluginCatalogItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 150 },
    {
      title: '类型',
      dataIndex: 'kind',
      width: 120,
      renderText: (kind) => pluginKindLabel(String(kind || '')),
    },
    { title: '名称', dataIndex: 'displayName', width: 180 },
    { title: '说明', dataIndex: 'description', ellipsis: true },
  ];

  if (!access.canEditLibraryPlugins) {
    return (
      <PageContainer title="插件">
        <Alert type="warning" message="需要 library:edit 权限" />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="插件"
      subTitle="查看已注册的元数据提取器、刮削器、分类器与 AI 提供方。"
      extra={
        <Button type="primary" onClick={() => history.push('/libraries')}>
          前往媒体库
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="库级配置"
        description="全局列表仅展示注册表。要为某个库启用 TMDb、FFprobe 或 AI 覆盖，请打开该库的插件配置页面。"
      />
      <ProTable<PluginCatalogItem>
        rowKey="id"
        search={false}
        pagination={false}
        request={async () => {
          const res = await listPlugins();
          return { data: res.data || [], success: res.code === 200 };
        }}
        columns={columns}
      />
      <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
        新建媒体库时会自动应用默认插件链，例如 NFO、FFprobe、EXIF、TMDb，可在库创建后继续调整。
      </Typography.Paragraph>
    </PageContainer>
  );
};

export default PluginsSettingsPage;
