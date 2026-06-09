import { PageContainer, ProForm, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { Alert, Button, Collapse, Form, message, Space, Tag } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useAccess } from '@umijs/max';
import {
  getMediaProcessingSettings,
  probeHardwareAcceleration,
  updateMediaProcessingSettings,
} from '@/services/settings';
import type { HardwareAccelerationProbe, MediaProcessingSettings } from '@/services/settings';
import { getSystemCapabilities, type SystemCapabilities } from '@/services/system';

type MediaProcessingFormValues = {
  ffmpegPath?: string;
  ffprobePath?: string;
  hardwareAcceleration?: string;
  hardwareDevice?: string;
  hardwareEncoder?: string;
};

const HARDWARE_ACCELERATION_OPTIONS = [
  { value: 'none', label: '无（软编码）' },
  { value: 'auto', label: '自动检测' },
  { value: 'nvenc', label: 'NVIDIA NVENC' },
  { value: 'qsv', label: 'Intel QSV' },
  { value: 'vaapi', label: 'VA-API（Intel/AMD）' },
  { value: 'amf', label: 'AMD AMF' },
];

const ENCODER_LABELS: Record<string, string> = {
  nvenc: 'NVENC',
  qsv: 'QSV',
  vaapi: 'VA-API',
  amf: 'AMF',
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

const EncoderProbeTags: React.FC<{ probe?: HardwareAccelerationProbe }> = ({ probe }) => {
  if (!probe?.encodersAvailable) return null;
  return (
    <Space wrap>
      {Object.entries(probe.encodersAvailable).map(([key, available]) => (
        <Tag key={key} color={available ? 'success' : 'default'}>
          {ENCODER_LABELS[key] || key}：{available ? '可用' : '不可用'}
        </Tag>
      ))}
    </Space>
  );
};

const MediaProcessingSettingsPage: React.FC = () => {
  const access = useAccess();
  const [form] = Form.useForm<MediaProcessingFormValues>();
  const [loading, setLoading] = useState(true);
  const [probing, setProbing] = useState(false);
  const [initial, setInitial] = useState<MediaProcessingFormValues>({});
  const [settings, setSettings] = useState<MediaProcessingSettings>();
  const [probe, setProbe] = useState<HardwareAccelerationProbe>();
  const [capabilities, setCapabilities] = useState<SystemCapabilities>();
  const hardwareAcceleration = Form.useWatch('hardwareAcceleration', form);

  const applySettings = useCallback(
    (next: MediaProcessingSettings) => {
      const values = {
        ffmpegPath: next.ffmpegPath,
        ffprobePath: next.ffprobePath,
        hardwareAcceleration: next.hardwareAcceleration || 'auto',
        hardwareDevice: next.hardwareDevice || '/dev/dri/renderD128',
        hardwareEncoder: next.hardwareEncoder || '',
      };
      setSettings(next);
      setInitial(values);
      if (next.hardwareProbe) {
        setProbe(next.hardwareProbe);
      }
      form.setFieldsValue(values);
    },
    [form],
  );

  const refreshCapabilities = useCallback(() => {
    getSystemCapabilities().then((res) => {
      if (res.code === 200 && res.data) {
        setCapabilities(res.data);
      }
    });
  }, []);

  const runProbe = useCallback(async () => {
    setProbing(true);
    try {
      const res = await probeHardwareAcceleration();
      if (res.code === 200 && res.data) {
        setProbe(res.data);
        message.success('硬件加速检测完成');
      }
    } finally {
      setProbing(false);
    }
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

  const showDevicePath =
    hardwareAcceleration === 'qsv' ||
    hardwareAcceleration === 'vaapi' ||
    hardwareAcceleration === 'auto';

  return (
    <PageContainer
      title="媒体处理"
      subTitle="FFmpeg / FFprobe 路径与 HLS 硬件加速（参考 Jellyfin 加速类型 + 设备路径 + 运行时探测）。"
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
            {capabilities?.hardwareAccelerationResolved && (
              <Tag color={capabilities.hardwareEncoderAvailable ? 'success' : 'default'}>
                硬编码：{capabilities.hardwareAccelerationResolved}
                {capabilities.hardwareEncoderAvailable ? '（可用）' : '（不可用，将软编码）'}
              </Tag>
            )}
          </Space>
        }
        description="保存后会立即用于缩略图、HLS 与元数据提取的新任务。Docker 环境需在 compose 中挂载 GPU 设备，详见 README「硬件加速」章节。"
      />

      {probe?.warnings && probe.warnings.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="硬件加速警告"
          description={
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              {probe.warnings.map((warning) => (
                <li key={warning}>{warning}</li>
              ))}
            </ul>
          }
        />
      )}

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

        <ProFormSelect
          name="hardwareAcceleration"
          label="硬件加速类型"
          options={HARDWARE_ACCELERATION_OPTIONS}
          extra="自动模式按 NVENC → QSV → VA-API → AMF 顺序选择；无 GPU 时 HLS 硬编码将回退软编码。"
        />

        {showDevicePath && (
          <ProFormText
            name="hardwareDevice"
            label="GPU 设备路径"
            placeholder="/dev/dri/renderD128"
            extra="Intel/AMD 集成显卡在 Linux 上通常使用 /dev/dri/renderD128；Docker 需挂载 /dev/dri。"
          />
        )}

        <div style={{ marginBottom: 24 }}>
          <Space wrap style={{ marginBottom: 8 }}>
            <span>编码器探测</span>
            <Button loading={probing} onClick={runProbe}>
              检测硬件加速
            </Button>
            {probe?.resolvedType && (
              <Tag>
                解析结果：{probe.resolvedType}
                {probe.resolvedEncoder ? ` → ${probe.resolvedEncoder}` : ''}
              </Tag>
            )}
          </Space>
          <EncoderProbeTags probe={probe} />
        </div>

        <Collapse
          ghost
          items={[
            {
              key: 'advanced',
              label: '高级：编码器覆盖',
              children: (
                <ProFormText
                  name="hardwareEncoder"
                  label="FFmpeg 编码器名称"
                  placeholder="留空则按加速类型使用模板（如 h264_nvenc）"
                  extra="仅当需要覆盖默认模板时填写，例如自定义 h264_nvenc 参数场景。"
                />
              ),
            },
          ]}
        />
      </ProForm>
    </PageContainer>
  );
};

export default MediaProcessingSettingsPage;
