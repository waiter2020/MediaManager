import React from 'react';
import { Alert, Progress } from 'antd';
import { SyncOutlined } from '@ant-design/icons';
import { useModel } from '@umijs/max';
import type { ScanProgress } from '@/models/global';

const ScanProgressBanner: React.FC = () => {
  const { scanStatus } = useModel('global');
  const scans = Object.values(scanStatus || {}) as ScanProgress[];
  if (scans.length === 0) {
    return null;
  }

  return (
    <div style={{ marginBottom: 12 }}>
      {scans.map((scan) => {
        const pct =
          scan.totalFiles > 0
            ? Math.min(100, Math.round((scan.scannedFiles / scan.totalFiles) * 100))
            : undefined;
        return (
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
              <div>
                <Progress
                  percent={pct}
                  size="small"
                  status="active"
                  format={() =>
                    `${scan.scannedFiles}/${scan.totalFiles || '?'} 文件 · 新增 ${scan.newItems ?? 0}`
                  }
                />
              </div>
            }
          />
        );
      })}
    </div>
  );
};

export default ScanProgressBanner;
