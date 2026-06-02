import React, { useEffect } from 'react';
import { Form, Input, InputNumber, Switch } from 'antd';

type PluginConfigValue = string | number | boolean | string[] | undefined;
type PluginConfigValues = Record<string, PluginConfigValue>;

interface Props {
  pluginId: string;
  value?: string;
  onChange?: (json: string) => void;
}

/** Structured editor for common metadata extractor plugin configs. */
const PluginExtractorConfigForm: React.FC<Props> = ({ pluginId, value, onChange }) => {
  const [form] = Form.useForm<PluginConfigValues>();
  const id = pluginId.toUpperCase();

  useEffect(() => {
    let parsed: PluginConfigValues = {};
    if (value?.trim()) {
      try {
        const raw: unknown = JSON.parse(value);
        parsed = raw && typeof raw === 'object' && !Array.isArray(raw) ? (raw as PluginConfigValues) : {};
      } catch {
        parsed = {};
      }
    }
    form.setFieldsValue(parsed);
  }, [value, form, id]);

  const emit = (_: PluginConfigValues, all: PluginConfigValues) => {
    const payload = Object.fromEntries(
      Object.entries(all).filter(([, v]) => v !== undefined && v !== null && v !== ''),
    );
    onChange?.(JSON.stringify(payload, null, 2));
  };

  if (id === 'MOCK') {
    return (
      <Form form={form} layout="vertical" size="small" onValuesChange={emit}>
        <Form.Item name="mockTitle" label="模拟标题" tooltip="刮削/测试时写入的标题">
          <Input placeholder="Test Movie Title" />
        </Form.Item>
        <Form.Item name="mockOverview" label="模拟简介">
          <Input.TextArea rows={2} placeholder="Optional overview text" />
        </Form.Item>
      </Form>
    );
  }

  if (id === 'TMDB') {
    return (
      <Form form={form} layout="vertical" size="small" onValuesChange={emit}>
        <Form.Item name="apiKey" label="TMDb API Key" tooltip="留空则使用系统设置中的密钥">
          <Input.Password placeholder="可选，覆盖全局配置" />
        </Form.Item>
      </Form>
    );
  }

  if (id === 'JAVBUS') {
    return (
      <Form form={form} layout="vertical" size="small" onValuesChange={emit}>
        <Form.Item name="baseUrl" label="站点地址">
          <Input placeholder="https://www.javbus.com" />
        </Form.Item>
        <Form.Item name="proxyUrl" label="代理（可选）">
          <Input placeholder="http://127.0.0.1:7890" />
        </Form.Item>
      </Form>
    );
  }

  if (id === 'STASHDB') {
    return (
      <Form form={form} layout="vertical" size="small" onValuesChange={emit}>
        <Form.Item name="endpoint" label="GraphQL 端点">
          <Input placeholder="https://stashdb.org/graphql" />
        </Form.Item>
        <Form.Item name="apiKey" label="API Token">
          <Input.Password placeholder="Bearer token" />
        </Form.Item>
      </Form>
    );
  }

  if (id === 'NFO' || id === 'FFPROBE' || id === 'EXIF') {
    return (
      <>
        <TypographyHint>{id} 通常无需额外配置。</TypographyHint>
        <Input.TextArea
          rows={2}
          style={{ marginTop: 8 }}
          value={value}
          placeholder="{}"
          onChange={(e) => onChange?.(e.target.value)}
        />
      </>
    );
  }

  return (
    <Input.TextArea
      rows={3}
      value={value}
      placeholder='{"key":"value"}'
      onChange={(e) => onChange?.(e.target.value)}
    />
  );
};

const TypographyHint: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>{children}</span>
);

export default PluginExtractorConfigForm;
