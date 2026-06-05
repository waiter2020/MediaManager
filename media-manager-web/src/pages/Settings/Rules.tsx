import React, { useRef, useState } from 'react';
import {
  ModalForm,
  PageContainer,
  ProFormDigit,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProTable,
} from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, message, Popconfirm, Switch } from 'antd';
import {
  type ClassificationRule,
  createClassificationRule,
  deleteClassificationRule,
  listClassificationRules,
  updateClassificationRule,
} from '@/services/classificationRules';

const RULE_TYPES = [
  { label: '路径 (PATH)', value: 'PATH' },
  { label: '文件名 (FILENAME)', value: 'FILENAME' },
  { label: '正则 (REGEX)', value: 'REGEX' },
];

const TARGET_TYPES = [
  { label: '标签 (TAG)', value: 'TAG' },
  { label: '分类 (CATEGORY)', value: 'CATEGORY' },
];

const DEFAULT_RULE: ClassificationRule = {
  name: '',
  ruleType: 'PATH',
  expression: '',
  targetType: 'TAG',
  targetValue: '',
  enabled: true,
  priority: 0,
};

const RulesSettings: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ClassificationRule | null>(null);

  const columns: ProColumns<ClassificationRule>[] = [
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '类型', dataIndex: 'ruleType', width: 120 },
    { title: '表达式', dataIndex: 'expression', ellipsis: true },
    { title: '目标类型', dataIndex: 'targetType', width: 110 },
    { title: '目标值', dataIndex: 'targetValue', width: 140 },
    { title: '优先级', dataIndex: 'priority', width: 90 },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (_, record) => (
        <Switch
          checked={record.enabled !== false}
          onChange={async (checked) => {
            if (!record.id) return;
            const res = await updateClassificationRule(record.id, { ...record, enabled: checked });
            if (res.code === 200) {
              actionRef.current?.reload();
            } else {
              message.error(res.message || '更新失败');
            }
          }}
        />
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => [
        <a
          key="edit"
          onClick={() => {
            setEditing(record);
            setModalOpen(true);
          }}
        >
          编辑
        </a>,
        <Popconfirm
          key="del"
          title="确定删除此规则？"
          onConfirm={async () => {
            if (!record.id) return;
            const res = await deleteClassificationRule(record.id);
            if (res.code === 200) {
              message.success('已删除');
              actionRef.current?.reload();
            } else {
              message.error(res.message || '删除失败');
            }
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer title="分类规则" subTitle="按路径、文件名或正则自动写入标签和分类。">
      <ProTable<ClassificationRule>
        actionRef={actionRef}
        rowKey="id"
        search={false}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            新建规则
          </Button>,
        ]}
        request={async () => {
          const res = await listClassificationRules();
          return { data: res.data || [], success: res.code === 200 };
        }}
        columns={columns}
      />

      <ModalForm<ClassificationRule>
        title={editing ? '编辑规则' : '新建规则'}
        open={modalOpen}
        modalProps={{
          destroyOnClose: true,
          onCancel: () => setModalOpen(false),
        }}
        initialValues={editing ?? DEFAULT_RULE}
        onFinish={async (values) => {
          const payload: ClassificationRule = { ...DEFAULT_RULE, ...values };
          const res = editing?.id
            ? await updateClassificationRule(editing.id, payload)
            : await createClassificationRule(payload);
          if (res.code === 200) {
            message.success(editing ? '规则已更新' : '规则已创建');
            setModalOpen(false);
            actionRef.current?.reload();
            return true;
          }
          message.error(res.message || '保存失败');
          return false;
        }}
      >
        <ProFormText name="name" label="名称" rules={[{ required: true }]} />
        <ProFormSelect name="ruleType" label="规则类型" options={RULE_TYPES} rules={[{ required: true }]} />
        <ProFormText
          name="expression"
          label="表达式"
          rules={[{ required: true }]}
          tooltip="PATH/FILENAME 使用子串或 glob；REGEX 使用正则。"
        />
        <ProFormSelect name="targetType" label="目标类型" options={TARGET_TYPES} rules={[{ required: true }]} />
        <ProFormText name="targetValue" label="目标值" rules={[{ required: true }]} />
        <ProFormDigit name="priority" label="优先级" min={0} fieldProps={{ precision: 0 }} />
        <ProFormSwitch name="enabled" label="启用" />
      </ModalForm>
    </PageContainer>
  );
};

export default RulesSettings;
