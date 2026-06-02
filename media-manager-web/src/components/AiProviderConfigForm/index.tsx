import React, { useEffect } from 'react';
import { Form, Input } from 'antd';

export interface AiProviderConfigValues {
  baseUrl?: string;
  apiKey?: string;
  llmModel?: string;
  embedModel?: string;
  outboundAllowed?: boolean;
}

interface Props {
  providerId: string;
  value?: string;
  onChange?: (json: string) => void;
}

const AiProviderConfigForm: React.FC<Props> = ({ providerId, value, onChange }) => {
  const [form] = Form.useForm<AiProviderConfigValues>();
  const isOpenAi = providerId === 'openai-compatible';

  useEffect(() => {
    let parsed: AiProviderConfigValues = {};
    if (value) {
      try {
        parsed = JSON.parse(value);
      } catch {
        parsed = {};
      }
    }
    form.setFieldsValue(parsed);
  }, [value, form]);

  const emit = (_: unknown, all: AiProviderConfigValues) => {
    const payload: AiProviderConfigValues = { ...all };
    if (!isOpenAi) {
      delete payload.apiKey;
    }
    onChange?.(JSON.stringify(payload, null, 2));
  };

  return (
    <Form form={form} layout="vertical" size="small" onValuesChange={emit}>
      <Form.Item name="baseUrl" label={isOpenAi ? 'API Base URL' : 'Ollama 地址'} rules={[{ required: true }]}>
        <Input placeholder={isOpenAi ? 'https://api.openai.com/v1' : 'http://localhost:11434'} />
      </Form.Item>
      {isOpenAi && (
        <Form.Item name="apiKey" label="API Key">
          <Input.Password placeholder="sk-..." />
        </Form.Item>
      )}
      <Form.Item name="llmModel" label="LLM 模型" rules={[{ required: true }]}>
        <Input placeholder={isOpenAi ? 'gpt-4o-mini' : 'qwen2.5:7b'} />
      </Form.Item>
      <Form.Item name="embedModel" label="Embedding 模型" rules={[{ required: true }]}>
        <Input placeholder={isOpenAi ? 'text-embedding-3-small' : 'nomic-embed-text'} />
      </Form.Item>
    </Form>
  );
};

export default AiProviderConfigForm;
