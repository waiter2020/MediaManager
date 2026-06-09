import { LoginForm, ProFormCheckbox, ProFormText } from '@ant-design/pro-components';
import { LockOutlined, LoginOutlined, UserOutlined } from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { message } from 'antd';
import React, { useEffect } from 'react';
import { login } from '@/services/auth';
import {
  getAccessToken,
  getRememberedUsername,
  isRememberLogin,
  setRememberedUsername,
  setSessionTokens,
} from '@/utils/authSession';
import AuthLayout from './AuthLayout';

interface LoginValues {
  username: string;
  password: string;
  remember?: boolean;
}

interface InitialState {
  isLogin?: boolean;
  setupCompleted?: boolean;
  currentUser?: API.CurrentUser;
}

const Login: React.FC = () => {
  const { initialState, setInitialState } = useModel('@@initialState');

  useEffect(() => {
    if (initialState?.isLogin || getAccessToken()) {
      history.replace('/');
    }
  }, [initialState?.isLogin]);

  const handleSubmit = async (values: LoginValues) => {
    const remember = values.remember ?? true;

    try {
      const res = await login(values);
      if (res.code === 200) {
        const { accessToken, refreshToken, user } = res.data;
        setSessionTokens({ accessToken, refreshToken }, { remember });
        setRememberedUsername(remember ? values.username : null);
        message.success('登录成功');
        await setInitialState((s: InitialState | undefined) => ({
          ...s,
          isLogin: true,
          setupCompleted: true,
          currentUser: {
            id: user?.id,
            username: user?.username,
            displayName: user?.displayName,
            avatarPath: user?.avatarPath,
            permissions: user?.permissions ?? [],
            roles: Array.isArray(user?.roles)
              ? user.roles.map((r: string | { code: string }) =>
                  typeof r === 'string' ? { code: r } : r,
                )
              : [],
          },
        }));
        history.push('/');
      } else {
        message.error(res.message || '登录失败');
      }
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } }).response?.status;
      if (status === 401) {
        message.error('用户名或密码错误');
      } else {
        message.error('登录失败：服务异常');
      }
    }
  };

  return (
    <AuthLayout
      eyebrow="媒体管理控制台"
      title="欢迎回来"
      description="继续整理媒体库、审核识别结果并恢复播放进度。"
    >
      <div className="auth-card-heading">
        <span className="auth-form-kicker">登录</span>
        <h2>进入 MediaManager</h2>
        <p>使用管理员或已授权账号进入系统。</p>
      </div>

      <LoginForm
        className="auth-form"
        contentStyle={{ width: '100%', minWidth: 0 }}
        initialValues={{
          username: getRememberedUsername() ?? '',
          remember: isRememberLogin(),
        }}
        submitter={{
          searchConfig: { submitText: '登录' },
          submitButtonProps: {
            size: 'large',
            icon: <LoginOutlined />,
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
            'data-testid': 'login-username',
          }}
          placeholder="用户名"
          rules={[{ required: true, message: '请输入用户名' }]}
        />
        <ProFormText.Password
          name="password"
          fieldProps={{
            size: 'large',
            prefix: <LockOutlined style={{ color: 'rgba(255,255,255,0.35)' }} />,
            'data-testid': 'login-password',
          }}
          placeholder="密码"
          rules={[{ required: true, message: '请输入密码' }]}
        />
        <ProFormCheckbox
          name="remember"
          fieldProps={{
            'data-testid': 'login-remember',
          }}
        >
          记住我
        </ProFormCheckbox>
      </LoginForm>
    </AuthLayout>
  );
};

export default Login;
