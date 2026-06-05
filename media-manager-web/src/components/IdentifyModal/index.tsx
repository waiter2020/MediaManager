import { Form, Input, Modal, Select, message } from 'antd';
import React, { useState } from 'react';
import {
  identifyItem,
  searchJavBusCandidates,
  searchStashDbCandidates,
  searchTmdbCandidates,
  type IdentifyCandidate,
} from '@/services/media';

export interface IdentifyModalProps {
  itemId: number;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface IdentifyFormValues {
  provider?: string;
  externalId: string | number;
}

const PROVIDERS = [
  { value: 'tmdb', label: 'TMDb' },
  { value: 'javbus', label: 'JavBus' },
  { value: 'stashdb', label: 'StashDB' },
];

const IdentifyModal: React.FC<IdentifyModalProps> = ({ itemId, open, onClose, onSuccess }) => {
  const [form] = Form.useForm<IdentifyFormValues>();
  const [provider, setProvider] = useState('tmdb');
  const [candidates, setCandidates] = useState<IdentifyCandidate[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const runSearch = async (q: string) => {
    if (!q?.trim()) {
      setCandidates([]);
      return;
    }
    setSearchLoading(true);
    try {
      const keyword = q.trim();
      const res =
        provider === 'tmdb'
          ? await searchTmdbCandidates(itemId, keyword)
          : provider === 'javbus'
            ? await searchJavBusCandidates(itemId, keyword)
            : await searchStashDbCandidates(itemId, keyword);
      if (res.code === 200) {
        setCandidates(res.data || []);
      }
    } finally {
      setSearchLoading(false);
    }
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const res = await identifyItem(itemId, {
        provider: values.provider || 'tmdb',
        externalId: String(values.externalId),
      });
      if (res.code === 200) {
        message.success('手动匹配成功');
        onClose();
        onSuccess();
      }
    } finally {
      setSubmitting(false);
    }
  };

  const candidateOptions = candidates.map((candidate) => {
    const id = String(candidate.externalId ?? candidate.id ?? '');
    const title = candidate.title || id;
    const extra = candidate.releaseDate || candidate.date || '';
    return {
      value: id,
      label: extra ? `${title} (${extra})` : title,
    };
  });

  return (
    <Modal
      title="手动匹配元数据"
      open={open}
      onCancel={() => {
        onClose();
        setCandidates([]);
        form.resetFields();
      }}
      onOk={handleOk}
      confirmLoading={submitting}
      width={560}
      destroyOnClose
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }} initialValues={{ provider: 'tmdb' }}>
        <Form.Item name="provider" label="数据源">
          <Select
            options={PROVIDERS}
            onChange={(value) => {
              setProvider(value);
              setCandidates([]);
            }}
          />
        </Form.Item>

        <Form.Item label="搜索">
          <Input.Search
            placeholder={
              provider === 'tmdb'
                ? '输入片名'
                : provider === 'javbus'
                  ? '输入番号'
                  : '输入标题或关键词'
            }
            loading={searchLoading}
            onSearch={runSearch}
            enterButton="搜索"
          />
        </Form.Item>

        {candidates.length > 0 && (
          <Form.Item label="选择匹配结果">
            <Select
              placeholder="从搜索结果选择"
              options={candidateOptions}
              onChange={(value) => form.setFieldValue('externalId', value)}
            />
          </Form.Item>
        )}

        <Form.Item
          name="externalId"
          label={provider === 'tmdb' ? 'TMDb ID' : provider === 'javbus' ? '番号' : 'Scene ID'}
          rules={[{ required: true, message: '请选择或输入外部 ID' }]}
        >
          <Input
            placeholder={
              provider === 'tmdb'
                ? '例如 27205'
                : provider === 'javbus'
                  ? '例如 SSIS-001'
                  : 'Scene ID'
            }
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default IdentifyModal;
