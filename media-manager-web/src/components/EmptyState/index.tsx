import React from 'react';
import { Button } from 'antd';
import {
  VideoCameraOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  InboxOutlined,
  FolderAddOutlined,
} from '@ant-design/icons';

const TYPE_ICONS: Record<string, React.ReactNode> = {
  MOVIE: <VideoCameraOutlined style={{ fontSize: 48 }} />,
  TV_SHOW: <VideoCameraOutlined style={{ fontSize: 48 }} />,
  IMAGE: <PictureOutlined style={{ fontSize: 48 }} />,
  AUDIO: <CustomerServiceOutlined style={{ fontSize: 48 }} />,
};

const TYPE_HINTS: Record<string, string> = {
  MOVIE: '还没有电影，请先添加媒体库并扫描',
  TV_SHOW: '还没有剧集，请先添加媒体库并扫描',
  IMAGE: '还没有图片，请先添加媒体库并扫描',
  AUDIO: '还没有音频，请先添加媒体库并扫描',
};

interface EmptyStateProps {
  type?: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
}

const EmptyState: React.FC<EmptyStateProps> = ({
  type,
  description,
  actionText,
  onAction,
}) => {
  const icon = type ? TYPE_ICONS[type] : <InboxOutlined style={{ fontSize: 48 }} />;
  const hint = description || (type ? TYPE_HINTS[type] : '暂无内容');

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '80px 20px',
        color: 'rgba(255,255,255,0.25)',
        textAlign: 'center',
      }}
    >
      <div style={{ marginBottom: 16, opacity: 0.4 }}>{icon}</div>
      <div
        style={{
          fontSize: 15,
          color: 'rgba(255,255,255,0.4)',
          marginBottom: actionText ? 24 : 0,
          maxWidth: 300,
        }}
      >
        {hint}
      </div>
      {actionText && onAction && (
        <Button type="primary" icon={<FolderAddOutlined />} onClick={onAction}>
          {actionText}
        </Button>
      )}
    </div>
  );
};

export default EmptyState;
