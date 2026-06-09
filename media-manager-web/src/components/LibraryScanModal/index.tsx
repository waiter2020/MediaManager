import { triggerScan } from '@/services/library';
import type { LibraryScanRequest } from '@/types/library';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { Checkbox, Form, Modal, Select, Tooltip, message } from 'antd';
import React, { useEffect } from 'react';
import { useAccess } from '@umijs/max';

export type LibraryScanFormValues = {
  refreshMetadata: boolean;
  scanMissingMetadata: boolean;
  reconcileMissing: boolean;
  scrapeAfterScan: boolean;
  scrapeTargetStatus: 'UNIDENTIFIED' | 'IDENTIFIED' | 'ALL';
  skipPostProcess: boolean;
};

const DEFAULT_VALUES: LibraryScanFormValues = {
  refreshMetadata: false,
  scanMissingMetadata: false,
  reconcileMissing: true,
  scrapeAfterScan: false,
  scrapeTargetStatus: 'UNIDENTIFIED',
  skipPostProcess: false,
};

const SCRAPE_TARGET_OPTIONS = [
  { label: '未识别', value: 'UNIDENTIFIED' },
  { label: '已识别', value: 'IDENTIFIED' },
  { label: '全部', value: 'ALL' },
];

export interface LibraryScanModalProps {
  open: boolean;
  libraryId?: number;
  libraryName?: string;
  onClose: () => void;
  onSuccess?: () => void;
}

const LibraryScanModal: React.FC<LibraryScanModalProps> = ({
  open,
  libraryId,
  libraryName,
  onClose,
  onSuccess,
}) => {
  const access = useAccess();
  const [form] = Form.useForm<LibraryScanFormValues>();
  const [submitting, setSubmitting] = React.useState(false);
  const scrapeAfterScan = Form.useWatch('scrapeAfterScan', form);

  useEffect(() => {
    if (open) {
      form.setFieldsValue(DEFAULT_VALUES);
    }
  }, [form, open]);

  const handleSubmit = async () => {
    if (!libraryId) {
      message.warning('请选择媒体库');
      return;
    }
    const values = await form.validateFields();
    const payload: LibraryScanRequest = {
      refreshMetadata: values.refreshMetadata,
      scanMissingMetadata: values.scanMissingMetadata,
      reconcileMissing: values.reconcileMissing,
      scrapeAfterScan: values.scrapeAfterScan,
      scrapeTargetStatus: values.scrapeTargetStatus,
      skipPostProcess: values.skipPostProcess,
    };

    setSubmitting(true);
    try {
      const res = await triggerScan(libraryId, payload);
      if (res.code === 200) {
        message.success('扫描任务已启动');
        onSuccess?.();
        onClose();
      }
    } finally {
      setSubmitting(false);
    }
  };

  const title = libraryName ? `扫描媒体库：${libraryName}` : '扫描媒体库';

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={submitting}
      okText="开始扫描"
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={DEFAULT_VALUES}>
        <Form.Item name="refreshMetadata" valuePropName="checked" style={{ marginBottom: 12 }}>
          <Checkbox
            onChange={(e) => {
              if (e.target.checked) {
                form.setFieldValue('scanMissingMetadata', false);
              }
            }}
          >
            刷新元数据
            <Tooltip title="对磁盘上未变更的已有文件全部重新运行本地元数据提取器（不联网）。">
              <QuestionCircleOutlined style={{ marginLeft: 6, color: 'rgba(0,0,0,0.45)' }} />
            </Tooltip>
          </Checkbox>
        </Form.Item>

        <Form.Item name="scanMissingMetadata" valuePropName="checked" style={{ marginBottom: 12 }}>
          <Checkbox
            onChange={(e) => {
              if (e.target.checked) {
                form.setFieldValue('refreshMetadata', false);
              }
            }}
          >
            扫描缺失的元数据
            <Tooltip title="仅对元数据不完整的未变更文件重新提取（如未识别、缺少时长/编码、无简介或封面等）。">
              <QuestionCircleOutlined style={{ marginLeft: 6, color: 'rgba(0,0,0,0.45)' }} />
            </Tooltip>
          </Checkbox>
        </Form.Item>

        <Form.Item name="reconcileMissing" valuePropName="checked" style={{ marginBottom: 12 }}>
          <Checkbox>
            清理缺失文件
            <Tooltip title="扫描结束后，将磁盘上已不存在的媒体文件标记为已删除。">
              <QuestionCircleOutlined style={{ marginLeft: 6, color: 'rgba(0,0,0,0.45)' }} />
            </Tooltip>
          </Checkbox>
        </Form.Item>

        {access.canExecuteScrape && (
          <>
            <Form.Item name="scrapeAfterScan" valuePropName="checked" style={{ marginBottom: 12 }}>
              <Checkbox>
                扫描后自动刮削
                <Tooltip title="扫描成功完成后，自动创建远程元数据刮削任务（TMDB 等）。">
                  <QuestionCircleOutlined style={{ marginLeft: 6, color: 'rgba(0,0,0,0.45)' }} />
                </Tooltip>
              </Checkbox>
            </Form.Item>

            {scrapeAfterScan && (
              <Form.Item label="刮削范围" name="scrapeTargetStatus">
                <Select options={SCRAPE_TARGET_OPTIONS} />
              </Form.Item>
            )}
          </>
        )}

        <Form.Item name="skipPostProcess" valuePropName="checked" style={{ marginBottom: 0 }}>
          <Checkbox>
            跳过后处理
            <Tooltip title="跳过分类、缩略图生成、AI 补全与搜索索引更新，可加快扫描速度。">
              <QuestionCircleOutlined style={{ marginLeft: 6, color: 'rgba(0,0,0,0.45)' }} />
            </Tooltip>
          </Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default LibraryScanModal;
