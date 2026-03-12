import React, { useState, useEffect } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Tree, Button, Modal, Form, Input, Select, message, Popconfirm, Space, Card, Empty } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/services/classification';

const CATEGORY_TYPES = [
  { label: '类型', value: 'GENRE' },
  { label: '年份', value: 'YEAR' },
  { label: '分辨率', value: 'RESOLUTION' },
  { label: '编码', value: 'CODEC' },
  { label: '自定义', value: 'CUSTOM' },
];

const CategoryManagement: React.FC = () => {
  const [treeData, setTreeData] = useState<any[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [parentId, setParentId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    const res = await getCategoryTree();
    setTreeData(res.data || []);
  };

  useEffect(() => { loadData(); }, []);

  const buildTreeData = (nodes: any[]): any[] => {
    return nodes.map((node) => ({
      key: node.id,
      title: (
        <Space>
          <span>{node.name}</span>
          <span style={{ color: '#888', fontSize: 12 }}>({node.type})</span>
          <a onClick={(e) => { e.stopPropagation(); setEditing(node); form.setFieldsValue(node); setModalVisible(true); }}><EditOutlined /></a>
          <Popconfirm title="确定删除？" onConfirm={async (e) => { e?.stopPropagation(); await deleteCategory(node.id); message.success('已删除'); loadData(); }}>
            <a style={{ color: '#ff4d4f' }} onClick={(e) => e.stopPropagation()}><DeleteOutlined /></a>
          </Popconfirm>
          <a onClick={(e) => { e.stopPropagation(); setEditing(null); setParentId(node.id); form.resetFields(); form.setFieldsValue({ parentId: node.id }); setModalVisible(true); }}>
            <PlusOutlined />
          </a>
        </Space>
      ),
      children: node.children ? buildTreeData(node.children) : undefined,
    }));
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      await updateCategory(editing.id, values);
      message.success('分类更新成功');
    } else {
      await createCategory(values);
      message.success('分类创建成功');
    }
    setModalVisible(false);
    setEditing(null);
    setParentId(null);
    form.resetFields();
    loadData();
  };

  return (
    <PageContainer title="分类管理">
      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            setEditing(null);
            setParentId(null);
            form.resetFields();
            setModalVisible(true);
          }}>
            新建根分类
          </Button>
        </Space>
        {treeData.length > 0 ? (
          <Tree
            treeData={buildTreeData(treeData)}
            defaultExpandAll
            showLine
          />
        ) : (
          <Empty description="暂无分类" />
        )}
      </Card>
      <Modal
        title={editing ? '编辑分类' : '新建分类'}
        open={modalVisible}
        onCancel={() => { setModalVisible(false); setEditing(null); }}
        onOk={handleSubmit}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="分类名" rules={[{ required: true }]}>
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
