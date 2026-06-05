import React, { useEffect } from 'react';
import { Form, Input } from 'antd';

export interface AiProviderConfigValues {
  baseUrl?: string;
  apiKey?: string;
  openaiLlmBaseUrl?: string;
  openaiEmbedBaseUrl?: string;
  llmApiKey?: string;
  embedApiKey?: string;
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
    if (isOpenAi) {
      parsed.openaiLlmBaseUrl = parsed.openaiLlmBaseUrl || parsed.baseUrl;
      parsed.openaiEmbedBaseUrl = parsed.openaiEmbedBaseUrl || parsed.baseUrl;
      parsed.llmApiKey = parsed.llmApiKey || parsed.apiKey;
      parsed.embedApiKey = parsed.embedApiKey || parsed.apiKey;
    }
    form.setFieldsValue(parsed);
  }, [value, form, isOpenAi]);

  const emit = (_: unknown, all: AiProviderConfigValues) => {
    const payload: AiProviderConfigValues = { ...all };
    if (!isOpenAi) {
      delete payload.apiKey;
      delete payload.llmApiKey;
      delete payload.embedApiKey;
      delete payload.openaiLlmBaseUrl;
      delete payload.openaiEmbedBaseUrl;
    }
    onChange?.(JSON.stringify(payload, null, 2));
  };

  return (
    <Form form={form} layout="vertical" size="small" onValuesChange={emit}>
      {isOpenAi ? (
        <>
          <Form.Item name="openaiLlmBaseUrl" label="LLM API Base URL" rules={[{ required: true }]}>
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item name="llmApiKey" label="LLM API Key">
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item name="openaiEmbedBaseUrl" label="Embedding API Base URL" rules={[{ required: true }]}>
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item name="embedApiKey" label="Embedding API Key">
            <Input.Password placeholder="sk-..." />
          </Form.Item>
        </>
      ) : (
        <Form.Item name="baseUrl" label="Ollama 地址" rules={[{ required: true }]}>
          <Input placeholder="http://localhost:11434" />
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
