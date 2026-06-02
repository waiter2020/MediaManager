import { PageContainer, ProForm, ProFormText } from '@ant-design/pro-components';
import { Alert, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import { getMediaProcessingSettings, updateMediaProcessingSettings } from '@/services/settings';

const MediaProcessingSettingsPage: React.FC = () => {
  const access = useAccess();
  const [loading, setLoading] = useState(true);
  const [initial, setInitial] = useState<{ ffmpegPath?: string; ffprobePath?: string }>({});

  useEffect(() => {
    if (!access.canManageSystem) return;
    getMediaProcessingSettings()
      .then((res) => {
        if (res.code === 200 && res.data) {
          setInitial({
            ffmpegPath: res.data.ffmpegPath,
            ffprobePath: res.data.ffprobePath,
          });
        }
      })
      .finally(() => setLoading(false));
  }, [access.canManageSystem]);

  if (!access.canManageSystem) {
    return (
      <PageContainer title="媒体处理">
        <Alert type="warning" message="需要 system:manage 权限" />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="媒体处理"
      subTitle="FFmpeg / FFprobe 可执行文件路径，用于缩略图、HLS 与元数据提取。"
    >
      <ProForm
        loading={loading}
        initialValues={initial}
        onFinish={async (values) => {
          const res = await updateMediaProcessingSettings(values);
          if (res.code === 200) {
            message.success('已保存');
            setInitial({
              ffmpegPath: res.data?.ffmpegPath,
              ffprobePath: res.data?.ffprobePath,
            });
          }
        }}
        submitter={{ searchConfig: { submitText: '保存' } }}
      >
        <ProFormText
          name="ffmpegPath"
          label="FFmpeg 路径"
          rules={[{ required: true, message: '请输入路径' }]}
          placeholder="ffmpeg 或绝对路径"
        />
        <ProFormText
          name="ffprobePath"
          label="FFprobe 路径"
          rules={[{ required: true, message: '请输入路径' }]}
          placeholder="ffprobe 或绝对路径"
        />
      </ProForm>
    </PageContainer>
  );
};

export default MediaProcessingSettingsPage;
