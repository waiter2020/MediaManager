import { PageContainer } from '@ant-design/pro-components';
import { Card, Empty, Typography } from 'antd';
import React from 'react';

const RulesSettings: React.FC = () => {
  return (
    <PageContainer title="分类规则管理">
      <Card>
        <Typography.Paragraph>分类规则允许您根据文件属性（分辨率、编码、路径等）自动给媒体项打标签和分类。</Typography.Paragraph>
        <Empty description="规则管理功能正在开发中" />
      </Card>
    </PageContainer>
  );
};

export default RulesSettings;
