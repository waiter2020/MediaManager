import React, { useRef, useState } from 'react';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { getLibraries } from '@/services/library';
import {
  assignRoles,
  createUser,
  deleteUser,
  getLibraryAccess,
  getUsers,
  setLibraryAccess,
  updateUser,
  type LibraryAccessItem,
  type UserCreatePayload,
  type UserUpdatePayload,
} from '@/services/user';
import type { MediaLibrary } from '@/types/library';
import type { Role, User } from '@/types/user';

const ROLE_OPTIONS = [
  { label: '超级管理员', value: 'SUPER_ADMIN' },
  { label: '管理员', value: 'ADMIN' },
  { label: '普通用户', value: 'USER' },
  { label: '访客', value: 'GUEST' },
];

const ROLE_COLORS: Record<string, string> = {
  SUPER_ADMIN: 'red',
  ADMIN: 'orange',
  USER: 'blue',
  GUEST: 'default',
};

const UsersManagement: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [createVisible, setCreateVisible] = useState(false);
  const [editVisible, setEditVisible] = useState(false);
  const [roleVisible, setRoleVisible] = useState(false);
  const [accessVisible, setAccessVisible] = useState(false);
  const [libraries, setLibraries] = useState<MediaLibrary[]>([]);
  const [accessRows, setAccessRows] = useState<LibraryAccessItem[]>([]);
  const [accessLoading, setAccessLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [createForm] = Form.useForm<UserCreatePayload>();
  const [editForm] = Form.useForm<UserUpdatePayload>();
  const [roleForm] = Form.useForm<{ roleCodes: string[] }>();

  const openLibraryAccess = async (record: User) => {
    setCurrentUser(record);
    setAccessVisible(true);
    setAccessLoading(true);
    try {
      const [libRes, accRes] = await Promise.all([getLibraries(), getLibraryAccess(record.id)]);
      const libs = libRes.code === 200 ? libRes.data || [] : [];
      setLibraries(libs);
      const existing = accRes.code === 200 ? accRes.data || [] : [];
      setAccessRows(
        libs.map((lib) => {
          const row = existing.find((item) => item.libraryId === lib.id);
          return {
            libraryId: lib.id,
            canView: row?.canView ?? false,
            canEdit: row?.canEdit ?? false,
            canDeleteFile: row?.canDeleteFile ?? false,
          };
        }),
      );
    } finally {
      setAccessLoading(false);
    }
  };

  const updateAccessRow = (index: number, patch: Partial<LibraryAccessItem>) => {
    setAccessRows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], ...patch };
      return next;
    });
  };

  const columns: ProColumns<User>[] = [
    { title: '用户名', dataIndex: 'username', width: 120 },
    { title: '显示名', dataIndex: 'displayName', width: 120 },
    { title: '邮箱', dataIndex: 'email', width: 180, ellipsis: true },
    {
      title: '角色',
      dataIndex: 'roles',
      width: 200,
      render: (_, record) => (
        <Space size={4} wrap>
          {record.roles?.map((role: Role) => (
            <Tag key={role.code} color={ROLE_COLORS[role.code] || 'default'}>
              {role.name || role.code}
            </Tag>
          ))}
        </Space>
      ),
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (_, record) => (
        <Switch
          checked={record.enabled}
          size="small"
          onChange={async (checked) => {
            await updateUser(record.id, { enabled: checked });
            actionRef.current?.reload();
          }}
        />
      ),
      search: false,
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => [
        <a
          key="edit"
          onClick={() => {
            setCurrentUser(record);
            editForm.setFieldsValue(record);
            setEditVisible(true);
          }}
        >
          编辑
        </a>,
        <a
          key="role"
          onClick={() => {
            setCurrentUser(record);
            roleForm.setFieldsValue({ roleCodes: record.roles?.map((role) => role.code) || [] });
            setRoleVisible(true);
          }}
        >
          角色
        </a>,
        <a key="access" onClick={() => openLibraryAccess(record)}>
          库权限
        </a>,
        <Popconfirm
          key="delete"
          title="确定删除此用户？"
          onConfirm={async () => {
            await deleteUser(record.id);
            message.success('已删除');
            actionRef.current?.reload();
          }}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer title="用户管理">
      <ProTable<User>
        actionRef={actionRef}
        columns={columns}
        rowKey="id"
        search={false}
        request={async () => {
          const res = await getUsers();
          return { data: res.data || [], success: true };
        }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              createForm.resetFields();
              setCreateVisible(true);
            }}
          >
            新建用户
          </Button>,
        ]}
      />

      <Modal
        title="新建用户"
        open={createVisible}
        onCancel={() => setCreateVisible(false)}
        onOk={async () => {
          const values = await createForm.validateFields();
          await createUser(values);
          message.success('用户创建成功。若为 USER/GUEST，请通过库权限分配可访问的媒体库。');
          setCreateVisible(false);
          actionRef.current?.reload();
        }}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, min: 3, message: '至少 3 个字符' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6, message: '至少 6 个字符' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="displayName" label="显示名"><Input /></Form.Item>
          <Form.Item name="email" label="邮箱"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑用户"
        open={editVisible}
        onCancel={() => setEditVisible(false)}
        onOk={async () => {
          if (!currentUser) return;
          const values = await editForm.validateFields();
          await updateUser(currentUser.id, values);
          message.success('用户更新成功');
          setEditVisible(false);
          actionRef.current?.reload();
        }}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="displayName" label="显示名"><Input /></Form.Item>
          <Form.Item name="email" label="邮箱"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`库访问权限 / ${currentUser?.username || ''}`}
        open={accessVisible}
        onCancel={() => setAccessVisible(false)}
        width={640}
        onOk={async () => {
          if (!currentUser) return;
          const items = accessRows.filter((row) => row.canView || row.canEdit || row.canDeleteFile);
          await setLibraryAccess(currentUser.id, { items });
          message.success('库权限已保存');
          setAccessVisible(false);
        }}
      >
        <Table
          size="small"
          loading={accessLoading}
          pagination={false}
          rowKey="libraryId"
          dataSource={accessRows}
          columns={[
            {
              title: '媒体库',
              dataIndex: 'libraryId',
              render: (libraryId: number) => libraries.find((lib) => lib.id === libraryId)?.name || libraryId,
            },
            {
              title: '查看',
              dataIndex: 'canView',
              width: 70,
              render: (value: boolean, _record, index) => (
                <Switch size="small" checked={value} onChange={(checked) => updateAccessRow(index, { canView: checked })} />
              ),
            },
            {
              title: '编辑',
              dataIndex: 'canEdit',
              width: 70,
              render: (value: boolean, _record, index) => (
                <Switch
                  size="small"
                  checked={value}
                  onChange={(checked) => updateAccessRow(index, { canEdit: checked, canView: checked ? true : accessRows[index].canView })}
                />
              ),
            },
            {
              title: '删文件',
              dataIndex: 'canDeleteFile',
              width: 80,
              render: (value: boolean, _record, index) => (
                <Switch
                  size="small"
                  checked={value}
                  onChange={(checked) => updateAccessRow(index, { canDeleteFile: checked, canView: checked ? true : accessRows[index].canView })}
                />
              ),
            },
          ]}
        />
      </Modal>

      <Modal
        title="分配角色"
        open={roleVisible}
        onCancel={() => setRoleVisible(false)}
        onOk={async () => {
          if (!currentUser) return;
          const values = await roleForm.validateFields();
          await assignRoles(currentUser.id, values);
          message.success('角色分配成功');
          setRoleVisible(false);
          actionRef.current?.reload();
        }}
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="roleCodes" label="角色">
            <Select mode="multiple" options={ROLE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default UsersManagement;
