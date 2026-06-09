import React, { useEffect, useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  ColorPicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, RobotOutlined, StopOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { history, useAccess } from '@umijs/max';
import {
  applyAiOrganization,
  cancelAiOrganization,
  getAiOrganizationStatus,
  previewAiOrganization,
  type AiOrganizationDuplicateGroup,
  type AiOrganizationJobStatus,
  type AiOrganizationRequest,
  type AiOrganizationResponse,
  type AiOrganizationSemanticMergeGroup,
  type AiOrganizationSmartCollectionCandidate,
  type AiOrganizationTagUsage,
} from '@/services/ai';
import {
  cancelLibraryClassify,
  classifyLibrary,
  createTag,
  deleteTag,
  getLibraryClassifyStatus,
  getTags,
  updateTag,
  type LibraryClassifyStatus,
  type TagItem,
  type TagPayload,
} from '@/services/classification';
import { getLibraries } from '@/services/library';
import type { MediaLibrary } from '@/types/library';

const TagsManagement: React.FC = () => {
  const access = useAccess();
  const actionRef = useRef<ActionType>();
  const previewDebounceRef = useRef<number>();
  const lastOrganizationFinishedAtRef = useRef<number>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTag, setEditingTag] = useState<TagItem | null>(null);
  const [form] = Form.useForm<TagPayload>();
  const [organizeOpen, setOrganizeOpen] = useState(false);
  const [organizeLoading, setOrganizeLoading] = useState(false);
  const [organizeApplying, setOrganizeApplying] = useState(false);
  const [classifyLoading, setClassifyLoading] = useState(false);
  const [libraries, setLibraries] = useState<MediaLibrary[]>([]);
  const [selectedLibraryId, setSelectedLibraryId] = useState<number | undefined>();
  const [mergeDuplicateTags, setMergeDuplicateTags] = useState(true);
  const [mergeAggressiveness, setMergeAggressiveness] =
    useState<AiOrganizationRequest['mergeAggressiveness']>('aggressive');
  const [deleteUnusedTags, setDeleteUnusedTags] = useState(true);
  const [deleteLowUsageTags, setDeleteLowUsageTags] = useState(true);
  const [protectManualTags, setProtectManualTags] = useState(true);
  const [recolorTags, setRecolorTags] = useState(true);
  const [recolorManualTags, setRecolorManualTags] = useState(false);
  const [createSmartCollections, setCreateSmartCollections] = useState(true);
  const [lowUsageThreshold, setLowUsageThreshold] = useState(1);
  const [maxCollections, setMaxCollections] = useState(0);
  const [minCollectionTagUsage, setMinCollectionTagUsage] = useState(3);
  const [minTagCollectionUsage, setMinTagCollectionUsage] = useState(10);
  const [collectionItemLimit, setCollectionItemLimit] = useState(0);
  const [organizationPreview, setOrganizationPreview] = useState<AiOrganizationResponse | null>(null);
  const [organizationStatus, setOrganizationStatus] = useState<AiOrganizationJobStatus | null>(null);
  const [classifyStatus, setClassifyStatus] = useState<LibraryClassifyStatus | null>(null);

  const buildOrganizationRequest = (
    overrides?: Partial<AiOrganizationRequest>,
  ): AiOrganizationRequest => ({
    libraryId: selectedLibraryId,
    mergeDuplicateTags,
    mergeAggressiveness,
    deleteUnusedTags,
    deleteLowUsageTags,
    protectManualTags,
    recolorTags,
    recolorManualTags,
    createSmartCollections,
    lowUsageThreshold,
    maxCollections,
    minCollectionTagUsage,
    minTagCollectionUsage,
    collectionItemLimit,
    ...overrides,
  });

  const loadLibraries = async () => {
    const res = await getLibraries();
    if (res.code === 200) {
      setLibraries(res.data || []);
    }
  };

  useEffect(() => {
    return () => {
      if (previewDebounceRef.current) {
        window.clearTimeout(previewDebounceRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!organizeOpen) {
      return undefined;
    }
    let active = true;
    const refreshStatuses = async () => {
      const [organizationResult, classifyResult] = await Promise.allSettled([
        getAiOrganizationStatus(),
        getLibraryClassifyStatus(),
      ]);
      if (!active) {
        return;
      }
      if (organizationResult.status === 'fulfilled' && organizationResult.value.code === 200) {
        const nextStatus = organizationResult.value.data || null;
        setOrganizationStatus(nextStatus);
        if (
          nextStatus?.result &&
          nextStatus.finishedAt &&
          lastOrganizationFinishedAtRef.current !== nextStatus.finishedAt
        ) {
          lastOrganizationFinishedAtRef.current = nextStatus.finishedAt;
          setOrganizationPreview(nextStatus.result);
          actionRef.current?.reload();
        }
      }
      if (classifyResult.status === 'fulfilled' && classifyResult.value.code === 200) {
        setClassifyStatus(classifyResult.value.data || null);
      }
    };

    refreshStatuses();
    const timer = window.setInterval(refreshStatuses, 1500);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [organizeOpen]);

  const loadOrganizationPreview = async (overrides?: Partial<AiOrganizationRequest>) => {
    setOrganizeLoading(true);
    try {
      const res = await previewAiOrganization(buildOrganizationRequest(overrides));
      if (res.code === 200) {
        setOrganizationPreview(res.data || null);
      } else {
        message.error(res.message || '预览失败');
      }
    } finally {
      setOrganizeLoading(false);
    }
  };

  const scheduleOrganizationPreview = (
    overrides?: Partial<AiOrganizationRequest>,
    delayMs = 300,
  ) => {
    if (previewDebounceRef.current) {
      window.clearTimeout(previewDebounceRef.current);
    }
    previewDebounceRef.current = window.setTimeout(() => {
      void loadOrganizationPreview(overrides);
    }, delayMs);
  };

  const openOrganizer = () => {
    setOrganizeOpen(true);
    void loadLibraries();
    void loadOrganizationPreview();
  };

  const handleApplyOrganization = async () => {
    setOrganizeApplying(true);
    try {
      const res = await applyAiOrganization(buildOrganizationRequest());
      if (res.code === 200) {
        setOrganizationStatus(res.data?.status || null);
        if (res.data?.accepted) {
          message.success(res.data.message || '整理任务已进入后台队列');
        } else {
          message.info(res.data?.message || '已有整理任务正在进行');
        }
      } else {
        message.error(res.message || '整理失败');
      }
    } finally {
      setOrganizeApplying(false);
    }
  };

  const handleStartAiTagging = async () => {
    if (!selectedLibraryId) {
      message.warning('请选择媒体库');
      return;
    }
    setClassifyLoading(true);
    try {
      const res = await classifyLibrary(selectedLibraryId);
      if (res.code === 200) {
        setClassifyStatus(res.data?.status || null);
        if (res.data?.accepted) {
          message.success(res.data.message || 'AI 打标任务已进入后台队列');
        } else {
          message.info(res.data?.message || '已有 AI 打标任务正在进行');
        }
      } else {
        message.error(res.message || '启动失败');
      }
    } finally {
      setClassifyLoading(false);
    }
  };

  const handleCancelOrganization = async () => {
    const res = await cancelAiOrganization();
    if (res.code === 200 && res.data) {
      message.info('已请求取消整理任务');
    }
  };

  const handleCancelAiTagging = async () => {
    const res = await cancelLibraryClassify();
    if (res.code === 200 && res.data) {
      message.info('已请求取消 AI 打标任务');
    }
  };

  const columns: ProColumns<TagItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 60, search: false },
    {
      title: '标签名',
      dataIndex: 'name',
      width: 150,
      render: (_, record) => <Tag color={record.color || undefined}>{record.name}</Tag>,
    },
    { title: '颜色', dataIndex: 'color', width: 100, search: false, render: (text) => text || '-' },
    { title: '来源', dataIndex: 'source', width: 100, search: false },
    {
      title: '关联媒体',
      dataIndex: 'usageCount',
      width: 110,
      search: false,
      render: (_, record) => {
        const count = record.usageCount || 0;
        if (count <= 0) {
          return <Typography.Text type="secondary">0</Typography.Text>;
        }
        return (
          <Button type="link" size="small" onClick={() => history.push(`/browse?tagIds=${record.id}`)}>
            {count}
          </Button>
        );
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      render: (_, record) => [
        <a
          key="edit"
          onClick={() => {
            setEditingTag(record);
            form.setFieldsValue({ name: record.name, color: record.color });
            setModalVisible(true);
          }}
        >
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title="确定删除此标签？"
          onConfirm={async () => {
            await deleteTag(record.id);
            message.success('已删除');
            actionRef.current?.reload();
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const color =
      typeof values.color === 'string'
        ? values.color
        : (values.color as { toHexString?: () => string } | undefined)?.toHexString?.() || values.color;
    const payload: TagPayload = { name: values.name, color: color ? String(color) : undefined };

    if (editingTag) {
      await updateTag(editingTag.id, payload);
      message.success('标签更新成功');
    } else {
      await createTag(payload);
      message.success('标签创建成功');
    }
    setModalVisible(false);
    setEditingTag(null);
    form.resetFields();
    actionRef.current?.reload();
  };

  const tagPreviewColumns = [
    {
      title: '标签',
      dataIndex: 'name',
      render: (_: unknown, record: AiOrganizationTagUsage) => (
        <Tag color={record.color || undefined}>{record.name}</Tag>
      ),
    },
    { title: '来源', dataIndex: 'source', width: 100 },
    { title: '引用数', dataIndex: 'usageCount', width: 90 },
    { title: '原因', dataIndex: 'cleanupReason', width: 220 },
  ];

  const duplicateColumns = [
    {
      title: '保留标签',
      dataIndex: 'canonicalTag',
      render: (tag: AiOrganizationTagUsage) => <Tag color={tag?.color || undefined}>{tag?.name}</Tag>,
    },
    {
      title: '将合并',
      dataIndex: 'duplicateTags',
      render: (tags: AiOrganizationTagUsage[]) => (
        <Space wrap size={[4, 4]}>
          {(tags || []).map((tag) => (
            <Tag key={tag.id} color={tag.color || undefined}>
              {tag.name}
            </Tag>
          ))}
        </Space>
      ),
    },
  ];

  const semanticMergeColumns = [
    {
      title: '来源',
      dataIndex: 'source',
      width: 100,
      render: (source: AiOrganizationSemanticMergeGroup['source']) => {
        const labels: Record<string, string> = {
          EXACT: '精确',
          STRUCTURE: '结构',
          EMBEDDING: '向量',
          AI: 'AI',
        };
        return labels[source || ''] || source || '-';
      },
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 90,
      render: (confidence?: number) =>
        confidence != null ? `${Math.round(confidence * 100)}%` : '-',
    },
    {
      title: '保留标签',
      dataIndex: 'canonicalTag',
      render: (tag: AiOrganizationTagUsage) => <Tag color={tag?.color || undefined}>{tag?.name}</Tag>,
    },
    {
      title: '将合并',
      dataIndex: 'duplicateTags',
      render: (tags: AiOrganizationTagUsage[]) => (
        <Space wrap size={[4, 4]}>
          {(tags || []).map((tag) => (
            <Tag key={tag.id} color={tag.color || undefined}>
              {tag.name}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      width: 140,
    },
  ];

  const collectionCandidateColumns = [
    {
      title: '维度',
      dataIndex: 'dimensionLabel',
      width: 110,
      render: (_: unknown, record: AiOrganizationSmartCollectionCandidate) => (
        <Tag color={record.color || undefined}>{record.dimensionLabel || record.dimension || '规则'}</Tag>
      ),
    },
    {
      title: '候选合集',
      dataIndex: 'name',
      render: (_: unknown, record: AiOrganizationSmartCollectionCandidate) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.displayValue || record.tagName || record.categoryName || record.value || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '命中媒体', dataIndex: 'usageCount', width: 100 },
  ];

  const generatedCollections = organizationPreview?.generatedCollections || [];
  const organizationRunning =
    organizationStatus?.state === 'queued' || organizationStatus?.state === 'running';
  const classifyRunning =
    Boolean(classifyStatus?.running) || classifyStatus?.state === 'queued' || classifyStatus?.state === 'running';
  const maintenanceRunning = organizationRunning || classifyRunning;
  const organizationPercent =
    (organizationStatus?.total || 0) > 0
      ? Math.min(100, Math.round(((organizationStatus?.processed || 0) / (organizationStatus?.total || 1)) * 100))
      : 0;
  const classifyPercent =
    (classifyStatus?.total || 0) > 0
      ? Math.min(100, Math.round(((classifyStatus?.processed || 0) / (classifyStatus?.total || 1)) * 100))
      : 0;

  return (
    <PageContainer
      title="标签管理"
      extra={
        <Button type="link" onClick={() => history.push('/settings/rules')}>
          分类规则
        </Button>
      }
      >
      <ProTable<TagItem>
        actionRef={actionRef}
        columns={columns}
        rowKey="id"
        search={false}
        request={async () => {
          const res = await getTags();
          return { data: res.data || [], success: true };
        }}
        toolBarRender={() => [
          <Button key="ai-organize" icon={<RobotOutlined />} onClick={openOrganizer}>
            AI 整理
          </Button>,
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingTag(null);
              form.resetFields();
              setModalVisible(true);
            }}
          >
            新建标签
          </Button>,
        ]}
      />
      <Modal
        title={editingTag ? '编辑标签' : '新建标签'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          setEditingTag(null);
        }}
        onOk={handleSubmit}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="标签名" rules={[{ required: true, message: '请输入标签名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="color" label="颜色">
            <ColorPicker />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={
          <Space>
            <RobotOutlined />
            AI 整理标签与合集
          </Space>
        }
        open={organizeOpen}
        width={920}
        onCancel={() => setOrganizeOpen(false)}
        footer={[
          <Button key="close" onClick={() => setOrganizeOpen(false)}>
            关闭
          </Button>,
          organizationRunning ? (
            <Button
              key="cancel-organize"
              danger
              icon={<StopOutlined />}
              disabled={Boolean(organizationStatus?.cancelRequested)}
              onClick={handleCancelOrganization}
            >
              取消整理
            </Button>
          ) : null,
          classifyRunning ? (
            <Button
              key="cancel-classify"
              danger
              icon={<StopOutlined />}
              disabled={Boolean(classifyStatus?.cancelRequested)}
              onClick={handleCancelAiTagging}
            >
              取消 AI 打标
            </Button>
          ) : null,
          <Button
            key="classify"
            icon={<ThunderboltOutlined />}
            loading={classifyLoading}
            disabled={!selectedLibraryId || !access.canEditMetadata || maintenanceRunning}
            onClick={handleStartAiTagging}
          >
            启动 AI 打标
          </Button>,
          <Button key="preview" loading={organizeLoading} onClick={() => loadOrganizationPreview()}>
            刷新预览
          </Button>,
          <Button
            key="apply"
            type="primary"
            loading={organizeApplying}
            disabled={!access.canEditMetadata || maintenanceRunning}
            onClick={handleApplyOrganization}
          >
            启动整理
          </Button>,
        ].filter(Boolean)}
      >
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space wrap size={[12, 12]}>
            <Select
              allowClear
              style={{ width: 240 }}
              placeholder="全部可见媒体库"
              value={selectedLibraryId}
              options={libraries.map((library) => ({ label: library.name, value: library.id }))}
              onChange={(value) => {
                setSelectedLibraryId(value);
                scheduleOrganizationPreview({ libraryId: value });
              }}
            />
            <Space>
              <span>合并重复标签</span>
              <Switch checked={mergeDuplicateTags} onChange={setMergeDuplicateTags} />
            </Space>
            <Space>
              <span>合并强度</span>
              <Select
                style={{ width: 120 }}
                value={mergeAggressiveness}
                disabled={!mergeDuplicateTags}
                options={[
                  { label: '保守', value: 'conservative' },
                  { label: '标准', value: 'standard' },
                  { label: '积极', value: 'aggressive' },
                ]}
                onChange={(value) => {
                  setMergeAggressiveness(value);
                  scheduleOrganizationPreview({ mergeAggressiveness: value });
                }}
              />
            </Space>
            <Space>
              <span>清理空闲/脏标签</span>
              <Switch checked={deleteUnusedTags} onChange={setDeleteUnusedTags} />
            </Space>
            <Space>
              <span>清理单媒体标签</span>
              <Switch checked={deleteLowUsageTags} onChange={setDeleteLowUsageTags} />
            </Space>
            <Space>
              <span>保护手动标签</span>
              <Switch checked={protectManualTags} onChange={setProtectManualTags} />
            </Space>
            <Space>
              <span>重新配色</span>
              <Switch checked={recolorTags} onChange={setRecolorTags} />
            </Space>
            <Space>
              <span>包含手动配色</span>
              <Switch checked={recolorManualTags} onChange={setRecolorManualTags} disabled={!recolorTags} />
            </Space>
            <Space>
              <span>创建智能合集</span>
              <Switch checked={createSmartCollections} onChange={setCreateSmartCollections} />
            </Space>
          </Space>

          <Space wrap size={[12, 12]}>
            <Space>
              <span>低引用阈值</span>
              <InputNumber
                min={0}
                max={1000}
                value={lowUsageThreshold}
                onChange={(value) => setLowUsageThreshold(value ?? 1)}
              />
            </Space>
            <Space>
              <span>合集数量</span>
              <InputNumber
                min={0}
                value={maxCollections}
                placeholder="无限制"
                onChange={(value) => setMaxCollections(value ?? 0)}
              />
            </Space>
            <Space>
              <span>最少命中</span>
              <InputNumber
                min={1}
                max={1000}
                value={minCollectionTagUsage}
                onChange={(value) => setMinCollectionTagUsage(value || 3)}
              />
            </Space>
            <Space>
              <span>标签最少命中</span>
              <InputNumber
                min={1}
                max={1000}
                value={minTagCollectionUsage}
                onChange={(value) => setMinTagCollectionUsage(value || 10)}
              />
            </Space>
            <Space>
              <span>合集容量</span>
              <InputNumber
                min={0}
                value={collectionItemLimit}
                placeholder="无限制"
                onChange={(value) => setCollectionItemLimit(value ?? 0)}
              />
            </Space>
          </Space>

          {organizationStatus && organizationStatus.state !== 'idle' && (
            <Alert
              showIcon
              type={
                organizationStatus.state === 'failed'
                  ? 'error'
                  : organizationStatus.state === 'done'
                    ? 'success'
                    : organizationStatus.state === 'cancelled'
                      ? 'warning'
                      : 'info'
              }
              message={organizationStatus.message || '标签整理任务'}
              description={
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Progress
                    percent={organizationPercent}
                    size="small"
                    status={
                      organizationStatus.state === 'failed'
                        ? 'exception'
                        : organizationStatus.state === 'done'
                          ? 'success'
                          : organizationRunning
                            ? 'active'
                            : 'normal'
                    }
                  />
                  <Typography.Text type="secondary">
                    已处理 {organizationStatus.processed || 0}/{organizationStatus.total || 0}，失败{' '}
                    {organizationStatus.failed || 0}
                  </Typography.Text>
                </Space>
              }
            />
          )}

          {classifyStatus && classifyStatus.state !== 'idle' && (
            <Alert
              showIcon
              type={
                classifyStatus.state === 'failed'
                  ? 'error'
                  : classifyStatus.state === 'done'
                    ? 'success'
                    : classifyStatus.state === 'cancelled'
                      ? 'warning'
                      : 'info'
              }
              message={classifyStatus.message || 'AI 打标任务'}
              description={
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Progress
                    percent={classifyPercent}
                    size="small"
                    status={
                      classifyStatus.state === 'failed'
                        ? 'exception'
                        : classifyStatus.state === 'done'
                          ? 'success'
                          : classifyRunning
                            ? 'active'
                            : 'normal'
                    }
                  />
                  <Typography.Text type="secondary">
                    已处理 {classifyStatus.processed || 0}/{classifyStatus.total || 0}，失败{' '}
                    {classifyStatus.failed || 0}
                  </Typography.Text>
                </Space>
              }
            />
          )}

          <Descriptions bordered size="small" column={3}>
            <Descriptions.Item label="待清理标签">
              {organizationPreview?.cleanupTagCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="精确重复">
              {organizationPreview?.duplicateGroupCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="合并建议">
              {organizationPreview?.semanticMergeGroupCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="合集候选">
              {organizationPreview?.smartCollectionCandidateCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="已中文化标签">
              {organizationPreview?.translatedTagCount ?? organizationStatus?.translatedTagCount ?? 0}
            </Descriptions.Item>
          </Descriptions>

          {generatedCollections.length > 0 && (
            <Alert
              type="success"
              showIcon
              message={`已创建 ${organizationPreview?.createdCollectionCount || 0} 个智能合集`}
            />
          )}

          <Typography.Title level={5} style={{ margin: 0 }}>
            精确重复标签
          </Typography.Title>
          <Table<AiOrganizationDuplicateGroup>
            rowKey="semanticKey"
            size="small"
            loading={organizeLoading}
            pagination={{ pageSize: 5 }}
            columns={duplicateColumns}
            dataSource={organizationPreview?.duplicateTagGroups || []}
          />

          <Typography.Title level={5} style={{ margin: 0 }}>
            合并建议
          </Typography.Title>
          <Alert
            type="info"
            showIcon
            message="预览仅展示精确重复与结构合并建议。向量与 AI 同义合并会在启动整理后于后台执行。"
          />
          <Table<AiOrganizationSemanticMergeGroup>
            rowKey={(record) =>
              `${record.source || 'merge'}-${record.canonicalTag?.id || 'canonical'}-${(record.duplicateTags || [])
                .map((tag) => tag.id)
                .join('-')}`}
            size="small"
            loading={organizeLoading}
            pagination={{ pageSize: 5 }}
            columns={semanticMergeColumns}
            dataSource={organizationPreview?.semanticMergeGroups || []}
          />

          <Typography.Title level={5} style={{ margin: 0 }}>
            待清理标签
          </Typography.Title>
          <Table<AiOrganizationTagUsage>
            rowKey="id"
            size="small"
            loading={organizeLoading}
            pagination={{ pageSize: 5 }}
            columns={tagPreviewColumns}
            dataSource={organizationPreview?.cleanupTags || []}
          />

          <Typography.Title level={5} style={{ margin: 0 }}>
            智能合集候选
          </Typography.Title>
          <Table<AiOrganizationSmartCollectionCandidate>
            rowKey={(record) => record.key || record.name}
            size="small"
            loading={organizeLoading}
            pagination={false}
            columns={collectionCandidateColumns}
            dataSource={organizationPreview?.smartCollectionCandidates || []}
          />
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default TagsManagement;
