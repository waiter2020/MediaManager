import { PageContainer, ProForm, ProFormText, ProFormSwitch } from '@ant-design/pro-components';
import { Card, message, Spin } from 'antd';
import React, { useEffect, useState } from 'react';
import { request } from '@umijs/max';

const GeneralSettings: React.FC = () => {
  const [configs, setConfigs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    request('/api/v1/system/config', { method: 'GET' })
      .then(res => {
        if (res.code === 200) setConfigs(res.data || []);
      })
      .finally(() => setLoading(false));
  }, []);

  const handleSave = async (values: any) => {
    await request('/api/v1/system/config', { method: 'PUT', data: values });
    message.success('配置已保存');
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  const configMap: Record<string, string> = {};
  configs.forEach((c: any) => { configMap[c.key] = c.value; });

  return (
    <PageContainer title="系统设置">
      <Card>
        <ProForm
          initialValues={configMap}
          onFinish={handleSave}
        >
          {configs.map((c: any) => (
            <ProFormText key={c.key} name={c.key} label={c.description || c.key} />
          ))}
        </ProForm>
      </Card>
    </PageContainer>
  );
};

export default GeneralSettings;
