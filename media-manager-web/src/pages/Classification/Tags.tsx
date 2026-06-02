import React, { useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, ColorPicker, Form, Input, Modal, Popconfirm, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { createTag, deleteTag, getTags, updateTag, type TagItem, type TagPayload } from '@/services/classification';

const TagsManagement: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTag, setEditingTag] = useState<TagItem | null>(null);
  const [form] = Form.useForm<TagPayload>();

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
    </PageContainer>
  );
};

export default TagsManagement;
