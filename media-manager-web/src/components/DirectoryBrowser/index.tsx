import React, { useState, useEffect } from 'react';
import { Modal, List, Breadcrumb, message, Space } from 'antd';
import { FolderOutlined, HomeOutlined } from '@ant-design/icons';
import { getDirectories } from '@/services/system';

interface DirectoryBrowserProps {
  visible: boolean;
  onCancel: () => void;
  onSelect: (path: string) => void;
}

const DirectoryBrowser: React.FC<DirectoryBrowserProps> = ({ visible, onCancel, onSelect }) => {
  const [currentPath, setCurrentPath] = useState<string>('');
  const [directories, setDirectories] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [pathStack, setPathStack] = useState<{name: string, path: string}[]>([]);

  useEffect(() => {
    if (visible) {
      loadDirectories(currentPath);
    }
  }, [visible, currentPath]);

  const loadDirectories = async (path: string) => {
    setLoading(true);
    try {
      const res = await getDirectories(path);
      if (res.code === 200) {
        setDirectories(res.data);
      } else {
        message.error(res.message || '获取目录失败');
      }
    } catch (e) {
      message.error('请求失败');
    }
    setLoading(false);
  };

  const handleNavigate = (dir: any) => {
    setCurrentPath(dir.path);
    if (!currentPath) {
      setPathStack([{ name: dir.name, path: dir.path }]);
    } else {
      setPathStack([...pathStack, { name: dir.name, path: dir.path }]);
    }
  };

  const handleBreadcrumbClick = (index: number) => {
    if (index === -1) {
      setCurrentPath('');
      setPathStack([]);
    } else {
      const newStack = pathStack.slice(0, index + 1);
      setCurrentPath(newStack[newStack.length - 1].path);
      setPathStack(newStack);
    }
  };

  return (
    <Modal
      title="选择目录"
      open={visible}
      onCancel={onCancel}
      onOk={() => {
        if (!currentPath) {
          message.warning('请选择一个有效目录');
          return;
        }
        onSelect(currentPath);
        setCurrentPath('');
        setPathStack([]);
      }}
      okText="选择当前目录"
      cancelText="取消"
      width={600}
      bodyStyle={{ padding: '8px 0' }}
    >
      <div style={{ marginBottom: 16, padding: '0 24px' }}>
        <Breadcrumb>
          <Breadcrumb.Item onClick={() => handleBreadcrumbClick(-1)}>
            <span style={{ cursor: 'pointer' }}><HomeOutlined /> 根目录</span>
          </Breadcrumb.Item>
          {pathStack.map((p, index) => (
            <Breadcrumb.Item key={p.path} onClick={() => handleBreadcrumbClick(index)}>
              <span style={{ cursor: 'pointer' }}>{p.name}</span>
            </Breadcrumb.Item>
          ))}
        </Breadcrumb>
      </div>
      
      <List
        loading={loading}
        dataSource={directories}
        style={{ height: 400, overflow: 'auto', borderTop: '1px solid #303030' }}
        renderItem={item => (
          <List.Item
            style={{ cursor: 'pointer', padding: '12px 24px' }}
            onClick={() => handleNavigate(item)}
            className="directory-item"
            onMouseEnter={(e) => {
              (e.currentTarget as HTMLDivElement).style.backgroundColor = 'rgba(255, 255, 255, 0.08)';
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLDivElement).style.backgroundColor = 'transparent';
            }}
          >
            <Space>
              <FolderOutlined style={{ color: '#1890ff', fontSize: 18 }} />
              <span style={{ fontSize: 14 }}>{item.name}</span>
            </Space>
          </List.Item>
        )}
      />
    </Modal>
  );
};

export default DirectoryBrowser;
