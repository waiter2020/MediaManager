import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { CheckCircleOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { message } from 'antd';
import React, { useEffect } from 'react';
import { getSetupStatus, setup } from '@/services/auth';
import AuthLayout from './AuthLayout';

interface SetupValues {
  username: string;
  password: string;
}

const Setup: React.FC = () => {
  useEffect(() => {
    getSetupStatus()
      .then((res) => {
        if (res.code === 200 && res.data?.setupCompleted) {
          history.replace('/login');
        }
      })
      .catch(() => {});
  }, []);

  const handleSubmit = async (values: SetupValues) => {
    try {
      const res = await setup(values);
      if (res.code === 200) {
        message.success('初始化成功，请登录');
        history.push('/login');
      } else {
        message.error(res.message || '初始化失败');
      }
    } catch {
      message.error('初始化请求失败');
    }
  };

  return (
    <AuthLayout
      tone="green"
      eyebrow="首次启动"
      title="建立第一位管理员"
      description="完成初始化后即可登录并开始整理媒体库。"
    >
      <div className="auth-card-heading">
        <span className="auth-form-kicker">初始化</span>
        <h2>创建管理员账号</h2>
        <p>此账号将拥有系统配置、媒体库和用户管理权限。</p>
      </div>

      <LoginForm
        className="auth-form"
        contentStyle={{ width: '100%', minWidth: 0 }}
        submitter={{
          searchConfig: { submitText: '创建并完成设置' },
          submitButtonProps: {
            size: 'large',
            icon: <CheckCircleOutlined />,
            className: 'auth-submit-button',
          },
        }}
        onFinish={handleSubmit}
      >
        <ProFormText
          name="username"
          fieldProps={{
            size: 'large',
            prefix: <UserOutlined style={{ color: 'rgba(255,255,255,0.35)' }} />,
          }}
          placeholder="管理员用户名"
          rules={[{ required: true, message: '请输入管理员用户名' }]}
        />
        <ProFormText.Password
          name="password"
          fieldProps={{
            size: 'large',
            prefix: <LockOutlined style={{ color: 'rgba(255,255,255,0.35)' }} />,
          }}
          placeholder="管理员密码"
          rules={[{ required: true, message: '请输入管理员密码' }]}
        />
      </LoginForm>
    </AuthLayout>
  );
};

export default Setup;
