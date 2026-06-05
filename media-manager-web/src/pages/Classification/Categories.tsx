import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Empty, Form, Input, Modal, Popconfirm, Select, Space, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  createCategory,
  deleteCategory,
  getCategoryTree,
  updateCategory,
  type CategoryItem,
  type CategoryPayload,
} from '@/services/classification';

const CATEGORY_TYPES = [
  { label: '类型', value: 'GENRE' },
  { label: '年份', value: 'YEAR' },
  { label: '分辨率', value: 'RESOLUTION' },
  { label: '编码', value: 'CODEC' },
  { label: '自定义', value: 'CUSTOM' },
];

const CategoryManagement: React.FC = () => {
  const [treeData, setTreeData] = useState<CategoryItem[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<CategoryItem | null>(null);
  const [parentId, setParentId] = useState<number | null>(null);
  const [form] = Form.useForm<CategoryPayload>();

  const loadData = async () => {
    const res = await getCategoryTree();
    setTreeData(res.data || []);
  };

  useEffect(() => {
    loadData();
  }, []);

  const openCreate = (nextParentId: number | null = null) => {
    setEditing(null);
    setParentId(nextParentId);
    form.resetFields();
    form.setFieldsValue({ parentId: nextParentId ?? undefined, type: 'CUSTOM' });
    setModalVisible(true);
  };

  const openEdit = (node: CategoryItem) => {
    setEditing(node);
    setParentId(node.parentId ?? null);
    form.setFieldsValue({
      name: node.name,
      type: node.type || 'CUSTOM',
      parentId: node.parentId,
    });
    setModalVisible(true);
  };

  const buildTreeData = (nodes: CategoryItem[]): DataNode[] =>
    nodes.map((node) => ({
      key: node.id,
      title: (
        <Space>
          <span>{node.name}</span>
          <span style={{ color: '#888', fontSize: 12 }}>({node.type || 'CUSTOM'})</span>
          <a
            onClick={(e) => {
              e.stopPropagation();
              openEdit(node);
            }}
          >
            <EditOutlined />
          </a>
          <Popconfirm
            title="确定删除？"
            onConfirm={async (e) => {
              e?.stopPropagation();
              await deleteCategory(node.id);
              message.success('已删除');
              loadData();
            }}
          >
            <a style={{ color: '#ff4d4f' }} onClick={(e) => e.stopPropagation()}>
              <DeleteOutlined />
            </a>
          </Popconfirm>
          <a
            onClick={(e) => {
              e.stopPropagation();
              openCreate(node.id);
            }}
          >
            <PlusOutlined />
          </a>
        </Space>
      ),
      children: node.children ? buildTreeData(node.children) : undefined,
    }));

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const payload: CategoryPayload = {
      ...values,
      parentId: values.parentId ? Number(values.parentId) : undefined,
    };
    if (editing) {
      await updateCategory(editing.id, payload);
      message.success('分类更新成功');
    } else {
      await createCategory(payload);
      message.success('分类创建成功');
    }
    setModalVisible(false);
    setEditing(null);
    setParentId(null);
    form.resetFields();
    loadData();
  };

  return (
    <PageContainer
      title="分类管理"
      extra={
        <Button type="link" onClick={() => history.push('/settings/rules')}>
          分类规则
        </Button>
      }
    >
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>
            新建根分类
          </Button>
        </Space>
        {treeData.length > 0 ? <Tree treeData={buildTreeData(treeData)} defaultExpandAll showLine /> : <Empty description="暂无分类" />}
      </Card>
      <Modal
        title={editing ? '编辑分类' : '新建分类'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          setEditing(null);
        }}
        onOk={handleSubmit}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="分类名" rules={[{ required: true, message: '请输入分类名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue="CUSTOM">
            <Select options={CATEGORY_TYPES} />
          </Form.Item>
          <Form.Item name="parentId" label="父分类 ID" hidden={!parentId && !editing}>
            <Input type="number" disabled={!!parentId && !editing} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default CategoryManagement;
