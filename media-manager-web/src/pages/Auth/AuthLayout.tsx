import React from 'react';
import {
  DatabaseOutlined,
  FolderOpenOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import './Auth.css';

type AuthTone = 'blue' | 'green';

interface AuthLayoutProps {
  eyebrow: string;
  title: string;
  description: string;
  tone?: AuthTone;
  children: React.ReactNode;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({
  eyebrow,
  title,
  description,
  tone = 'blue',
  children,
}) => (
  <main className={`auth-page auth-page-${tone}`}>
    <section className="auth-showcase">
      <div className="auth-brand">
        <div className="auth-logo">M</div>
        <div>
          <strong>MediaManager</strong>
          <span>{eyebrow}</span>
        </div>
      </div>

      <div className="auth-showcase-main">
        <p className="auth-eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>

      <div className="auth-visual" aria-hidden="true">
        <div className="auth-poster auth-poster-large">
          <PlayCircleOutlined />
          <span>4K</span>
        </div>
        <div className="auth-poster auth-poster-small auth-poster-warm">
          <DatabaseOutlined />
        </div>
        <div className="auth-poster auth-poster-small auth-poster-cool">
          <FolderOpenOutlined />
        </div>
      </div>

      <div className="auth-insights">
        <div className="auth-insight">
          <DatabaseOutlined />
          <span>媒体库</span>
        </div>
        <div className="auth-insight">
          <PlayCircleOutlined />
          <span>播放</span>
        </div>
        <div className="auth-insight">
          <SafetyCertificateOutlined />
          <span>权限</span>
        </div>
      </div>
    </section>

    <section className="auth-panel" aria-label={title}>
      <div className="auth-card">{children}</div>
    </section>
  </main>
);

export default AuthLayout;
