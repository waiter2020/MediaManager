import { PageContainer, ProForm, ProFormText, ProFormSelect, ProFormSwitch, ProFormDigit, ProFormList } from '@ant-design/pro-components';
import { message, Card, Input, Button, Spin } from 'antd';
import { history, useParams } from '@umijs/max';
import React, { useState, useEffect } from 'react';
import { createLibrary, getLibrary, updateLibrary } from '@/services/library';
import DirectoryBrowser from '@/components/DirectoryBrowser';
import { FolderOpenOutlined } from '@ant-design/icons';

const DirectoryInput: React.FC<{ value?: string; onChange?: (val: string) => void }> = ({ value, onChange }) => {
  const [visible, setVisible] = useState(false);
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <Input value={value} onChange={(e) => onChange?.(e.target.value)} placeholder="请输入绝对路径" />
      <Button icon={<FolderOpenOutlined />} onClick={() => setVisible(true)}>浏览</Button>
      <DirectoryBrowser
        visible={visible}
        onCancel={() => setVisible(false)}
        onSelect={(p) => { onChange?.(p); setVisible(false); }}
      />
    </div>
  );
};

const LibraryCreate: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const isEdit = !!params.id;
  const [initialValues, setInitialValues] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isEdit && params.id) {
      setLoading(true);
      getLibrary(Number(params.id))
        .then(res => {
          if (res.code === 200) {
            setInitialValues(res.data);
          }
        })
        .finally(() => setLoading(false));
    }
  }, [params.id]);

  const onFinish = async (values: any) => {
    const payload = {
      ...values,
      extractors: values.extractors || [
        { type: 'NFO', priority: 0, enabled: true },
        { type: 'FFPROBE', priority: 1, enabled: true },
        { type: 'TMDB', priority: 2, enabled: true, config: '{}' },
      ],
    };

    let res;
    if (isEdit) {
      res = await updateLibrary(Number(params.id), payload);
    } else {
      res = await createLibrary(payload);
    }

    if (res.code === 200) {
      message.success(isEdit ? '媒体库更新成功' : '媒体库创建成功');
      history.push('/libraries');
    } else {
      message.error(res.message || '操作失败');
    }
  };

  if (isEdit && loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;
  }

  return (
    <PageContainer title={isEdit ? '编辑媒体库' : '创建媒体库'}>
      <Card>
        <ProForm
          onFinish={onFinish}
          initialValues={initialValues || undefined}
          key={initialValues ? 'loaded' : 'new'}
        >
          <ProFormText name="name" label="媒体库名称" rules={[{ required: true }]} />
          <ProFormSelect
            name="type"
            label="类型"
            options={[
              { label: '电影', value: 'MOVIE' },
              { label: '剧集', value: 'TV_SHOW' },
              { label: '图片', value: 'IMAGE' },
              { label: '音频', value: 'AUDIO' },
              { label: '混合', value: 'MIXED' },
            ]}
            rules={[{ required: true }]}
            disabled={isEdit}
          />
          <ProFormSelect
            name="language"
            label="元数据语言"
            initialValue="zh"
            options={[
              { label: '中文 (zh)', value: 'zh' },
              { label: '英文 (en)', value: 'en' },
              { label: '日文 (ja)', value: 'ja' },
            ]}
          />
          <ProFormSwitch name="autoScan" label="启用自动扫描" initialValue={true} />
          <ProFormDigit name="scanIntervalMinutes" label="扫描间隔（分钟）" initialValue={30} min={5} max={1440} />
          <ProFormList name="paths" label="媒体库目录" creatorButtonProps={{ creatorButtonText: '添加目录' }}>
            <ProForm.Item name="path" rules={[{ required: true, message: '请输入目录路径' }]}>
              <DirectoryInput />
            </ProForm.Item>
          </ProFormList>
        </ProForm>
      </Card>
    </PageContainer>
  );
};

export default LibraryCreate;
