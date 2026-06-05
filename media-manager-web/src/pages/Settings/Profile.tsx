import React, { useEffect } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Card, Form, Input, Button, message } from 'antd';
import { getCurrentUser, updateCurrentUser, changePassword } from '@/services/user';

const ProfilePage: React.FC = () => {
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [loading, setLoading] = React.useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const res = await getCurrentUser();
        if (res.code === 200 && res.data) {
          profileForm.setFieldsValue({
            displayName: res.data.displayName,
            email: res.data.email,
          });
        }
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [profileForm]);

  const saveProfile = async () => {
    const values = await profileForm.validateFields();
    const res = await updateCurrentUser(values);
    if (res.code === 200) {
      message.success('资料已更新');
    }
  };

  const savePassword = async () => {
    const values = await passwordForm.validateFields();
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的新密码不一致');
      return;
    }
    const res = await changePassword({
      oldPassword: values.oldPassword,
      newPassword: values.newPassword,
    });
    if (res.code === 200) {
      message.success('密码已修改');
      passwordForm.resetFields();
    }
  };

  return (
    <PageContainer title="个人设置" loading={loading}>
      <Card title="基本资料" style={{ marginBottom: 24 }}>
        <Form form={profileForm} layout="vertical" style={{ maxWidth: 480 }}>
          <Form.Item name="displayName" label="显示名称">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input type="email" />
          </Form.Item>
          <Button type="primary" onClick={saveProfile}>
            保存
          </Button>
        </Form>
      </Card>
      <Card title="修改密码">
        <Form form={passwordForm} layout="vertical" style={{ maxWidth: 480 }}>
          <Form.Item name="oldPassword" label="当前密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="confirmPassword" label="确认新密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" onClick={savePassword}>
            更新密码
          </Button>
        </Form>
      </Card>
    </PageContainer>
  );
};

export default ProfilePage;
