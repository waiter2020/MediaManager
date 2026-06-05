import { PageContainer, ProForm, ProFormText } from '@ant-design/pro-components';
import { Alert, Form, message, Space, Tag } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import { getMediaProcessingSettings, updateMediaProcessingSettings } from '@/services/settings';
import type { MediaProcessingSettings } from '@/services/settings';
import { getSystemCapabilities, type SystemCapabilities } from '@/services/system';

type MediaProcessingFormValues = {
  ffmpegPath?: string;
  ffprobePath?: string;
};

const availabilityText = (available?: boolean) => {
  if (available === undefined) return '未检测';
  return available ? '可用' : '不可用';
};

const AvailabilityTag: React.FC<{ label: string; available?: boolean; path?: string }> = ({
  label,
  available,
  path,
}) => (
  <Tag color={available === undefined ? 'default' : available ? 'success' : 'error'}>
    {label}：{availabilityText(available)}
    {path ? `（${path}）` : ''}
  </Tag>
);

const MediaProcessingSettingsPage: React.FC = () => {
  const access = useAccess();
  const [form] = Form.useForm<MediaProcessingFormValues>();
  const [loading, setLoading] = useState(true);
  const [initial, setInitial] = useState<MediaProcessingFormValues>({});
  const [settings, setSettings] = useState<MediaProcessingSettings>();
  const [capabilities, setCapabilities] = useState<SystemCapabilities>();

  const applySettings = useCallback((next: MediaProcessingSettings) => {
    const values = {
      ffmpegPath: next.ffmpegPath,
      ffprobePath: next.ffprobePath,
    };
    setSettings(next);
    setInitial(values);
    form.setFieldsValue(values);
  }, [form]);

  const refreshCapabilities = useCallback(() => {
    getSystemCapabilities().then((res) => {
      if (res.code === 200 && res.data) {
        setCapabilities(res.data);
      }
    });
  }, []);

  useEffect(() => {
    if (!access.canManageSystem) {
      setLoading(false);
      return;
    }
    getMediaProcessingSettings()
      .then((res) => {
        if (res.code === 200 && res.data) {
          applySettings(res.data);
        }
      })
      .finally(() => setLoading(false));
    refreshCapabilities();
  }, [access.canManageSystem, applySettings, refreshCapabilities]);

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
      <Alert
        type={
          capabilities?.ffmpegAvailable === false || capabilities?.ffprobeAvailable === false
            ? 'warning'
            : 'info'
        }
        showIcon
        style={{ marginBottom: 16 }}
        message={
          <Space wrap>
            <span>运行时检测</span>
            <AvailabilityTag
              label="FFmpeg"
              available={capabilities?.ffmpegAvailable}
              path={capabilities?.ffmpegPath || settings?.ffmpegPath}
            />
            <AvailabilityTag
              label="FFprobe"
              available={capabilities?.ffprobeAvailable}
              path={capabilities?.ffprobePath || settings?.ffprobePath}
            />
          </Space>
        }
        description="保存后会立即用于缩略图、HLS 与元数据提取的新任务。"
      />
      <ProForm
        form={form}
        loading={loading}
        initialValues={initial}
        onFinish={async (values) => {
          const res = await updateMediaProcessingSettings(values);
          if (res.code === 200 && res.data) {
            message.success('已保存');
            applySettings(res.data);
            refreshCapabilities();
          }
        }}
        submitter={{ searchConfig: { submitText: '保存' } }}
      >
        <ProFormText
          name="ffmpegPath"
          label="FFmpeg 路径"
          rules={[{ required: true, message: '请输入路径' }]}
          placeholder="ffmpeg 或绝对路径"
          extra={settings ? `当前配置：${settings.ffmpegPath || '-'}` : undefined}
        />
        <ProFormText
          name="ffprobePath"
          label="FFprobe 路径"
          rules={[{ required: true, message: '请输入路径' }]}
          placeholder="ffprobe 或绝对路径"
          extra={settings ? `当前配置：${settings.ffprobePath || '-'}` : undefined}
        />
      </ProForm>
    </PageContainer>
  );
};

export default MediaProcessingSettingsPage;
