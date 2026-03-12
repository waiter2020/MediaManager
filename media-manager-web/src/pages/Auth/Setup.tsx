import { LoginForm, ProFormText } from '@ant-design/pro-components';
import { message } from 'antd';
import { history, request } from '@umijs/max';
import React from 'react';
import { LockOutlined, UserOutlined } from '@ant-design/icons';

const Setup: React.FC = () => {
  const handleSubmit = async (values: any) => {
    try {
      const res = await request('/api/v1/auth/setup', {
        method: 'POST',
        data: values,
      });
      if (res.code === 200) {
        message.success('初始化成功，请登录');
        history.push('/login');
      } else {
        message.error(res.message || '初始化失败');
      }
    } catch (e) {
      message.error('初始化请求失败');
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
      <div
        style={{
          position: 'absolute',
          top: '30%',
          right: '20%',
          width: 350,
          height: 350,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(82,196,26,0.06) 0%, transparent 70%)',
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
              background: 'linear-gradient(135deg, #52c41a, #73d13d)',
              marginBottom: 16,
              fontSize: 24,
              color: '#fff',
              fontWeight: 700,
            }}
          >
            M
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#fff', margin: '0 0 6px' }}>
            初始化设置
          </h1>
          <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.4)', margin: 0 }}>
            创建超级管理员账号
          </p>
        </div>

        <LoginForm
          submitter={{
            searchConfig: { submitText: '创建并完成设置' },
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
            placeholder="设置管理员用户名"
            rules={[{ required: true }]}
          />
          <ProFormText.Password
            name="password"
            fieldProps={{
              size: 'large',
              prefix: <LockOutlined style={{ color: 'rgba(255,255,255,0.3)' }} />,
              style: { borderRadius: 10 },
            }}
            placeholder="设置密码"
            rules={[{ required: true }]}
          />
        </LoginForm>
      </div>
    </div>
  );
};

export default Setup;
