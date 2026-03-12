import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { history, request } from '@umijs/max';
import React from 'react';
import { LockOutlined, UserOutlined } from '@ant-design/icons';

const Login: React.FC = () => {
  const handleSubmit = async (values: any) => {
    try {
      const res = await request('/api/v1/auth/login', {
        method: 'POST',
        data: values,
      });
      if (res.code === 200) {
        message.success('登录成功');
        localStorage.setItem('accessToken', res.data.accessToken);
        localStorage.setItem('refreshToken', res.data.refreshToken);
        history.push('/');
      } else {
        message.error(res.message || '登录失败');
      }
    } catch (e: any) {
      if (e.response?.status === 401) {
        message.error('用户名或密码错误');
      } else {
        message.error('登录失败: 服务异常');
      }
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0a0a18 0%, #0d1025 30%, #0a0a18 60%, #10101f 100%)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Subtle glow effects */}
      <div
        style={{
          position: 'absolute',
          top: '20%',
          left: '15%',
          width: 400,
          height: 400,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(22,104,220,0.08) 0%, transparent 70%)',
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: '10%',
          right: '10%',
          width: 300,
          height: 300,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(114,46,209,0.06) 0%, transparent 70%)',
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />

      <div
        style={{
          position: 'relative',
          zIndex: 1,
          width: '100%',
          maxWidth: 400,
          padding: '40px 36px',
          background: 'rgba(20, 20, 35, 0.7)',
          backdropFilter: 'blur(20px)',
          borderRadius: 20,
          border: '1px solid rgba(255,255,255,0.06)',
          boxShadow: '0 20px 60px rgba(0,0,0,0.4)',
          animation: 'scaleIn 0.5s ease-out',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 56,
              height: 56,
              borderRadius: 16,
              background: 'linear-gradient(135deg, #1668dc, #3b82f6)',
              marginBottom: 16,
              fontSize: 24,
              color: '#fff',
              fontWeight: 700,
            }}
          >
            M
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#fff', margin: '0 0 6px' }}>
            MediaManager
          </h1>
          <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.4)', margin: 0 }}>
            Self-hosted Media Platform
          </p>
        </div>

        <LoginForm
          submitter={{
            searchConfig: { submitText: '登录' },
            submitButtonProps: {
              size: 'large',
              style: {
                width: '100%',
                borderRadius: 10,
                height: 44,
                fontSize: 15,
                fontWeight: 600,
              },
            },
          }}
          onFinish={handleSubmit}
        >
          <ProFormText
            name="username"
            fieldProps={{
              size: 'large',
              prefix: <UserOutlined style={{ color: 'rgba(255,255,255,0.3)' }} />,
              style: { borderRadius: 10 },
            }}
            placeholder="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          />
          <ProFormText.Password
            name="password"
            fieldProps={{
              size: 'large',
              prefix: <LockOutlined style={{ color: 'rgba(255,255,255,0.3)' }} />,
              style: { borderRadius: 10 },
            }}
            placeholder="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          />
        </LoginForm>
      </div>
    </div>
  );
};

export default Login;
