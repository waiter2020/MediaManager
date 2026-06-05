import React from 'react';
import { Alert, Space, Tag, Tooltip } from 'antd';
import { SyncOutlined } from '@ant-design/icons';
import { useModel } from '@umijs/max';
import type { ScanProgress } from '@/models/global';

const scanErrorTooltip = (scan: ScanProgress) => {
  const errors = scan.recentErrors || [];
  if (errors.length === 0) {
    return <span>暂无失败明细，请查看系统日志</span>;
  }
  return <span style={{ whiteSpace: 'pre-line' }}>{errors.map((item) => `${item.path}: ${item.message}`).join('\n')}</span>;
};

const ScanProgressBanner: React.FC = () => {
  const { scanStatus } = useModel('global');
  const scans = Object.values(scanStatus || {}) as ScanProgress[];
  if (scans.length === 0) {
    return null;
  }

  return (
    <div style={{ marginBottom: 12 }}>
      {scans.map((scan) => (
        <Alert
          key={scan.libraryId}
          type="info"
          showIcon
          icon={<SyncOutlined spin />}
          style={{ marginBottom: 8 }}
          message={
            <span>
              正在扫描 <strong>{scan.libraryName || `库 #${scan.libraryId}`}</strong>
              {scan.currentPath ? ` · ${scan.currentPath}` : ''}
            </span>
          }
          description={
            <Space size={[8, 8]} wrap>
              <Tag color="processing">已检查 {scan.scannedFiles ?? 0}</Tag>
              <Tag color="blue">匹配 {scan.matchedFiles ?? 0}</Tag>
              <Tag color="green">新增 {scan.newItems ?? 0}</Tag>
              <Tag>更新 {scan.updatedItems ?? 0}</Tag>
              {(scan.failedItems ?? 0) > 0 ? (
                <Tooltip title={scanErrorTooltip(scan)}>
                  <Tag color="error">失败 {scan.failedItems}</Tag>
                </Tooltip>
              ) : null}
            </Space>
          }
        />
      ))}
    </div>
  );
};

export default ScanProgressBanner;
