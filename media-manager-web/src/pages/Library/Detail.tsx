import { PageContainer, ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Alert, Button, Card, Progress, Space, Statistic, Tag, message } from 'antd';
import { history, useAccess, useParams } from '@umijs/max';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  applyDefaultLibraryPlugins,
} from '@/services/plugin';
import {
  classifyLibrary,
  getLibraryClassifyStatus,
  type LibraryClassifyStatus,
} from '@/services/classification';
import LibraryScanModal from '@/components/LibraryScanModal';
import {
  getLibrary,
  getLibraryStats,
  type LibraryStats,
} from '@/services/library';
import { getItems } from '@/services/media';
import { createScrapeTask } from '@/services/scrape';
import { searchUnified } from '@/services/search';
import UnifiedSearchBox from '@/components/UnifiedSearchBox';
import { hasEnabledScraper, libraryTypeNeedsScraper, pluginKindLabel } from '@/utils/pluginLabels';
import type { MediaItem } from '@/types/media';
import type { LibraryPluginConfig } from '@/types/library';

type PluginSummary = Pick<LibraryPluginConfig, 'pluginId' | 'kind' | 'enabled'>;

interface LibraryStatsView extends LibraryStats {
  libraryName?: string;
  libraryType?: string;
}

const LibraryDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const access = useAccess();
  const actionRef = useRef<ActionType>();
  const [stats, setStats] = useState<LibraryStatsView | null>(null);
  const [plugins, setPlugins] = useState<PluginSummary[]>([]);
  const [scanModalOpen, setScanModalOpen] = useState(false);
  const [scraping, setScraping] = useState(false);
  const [classifying, setClassifying] = useState(false);
  const [classifyStatus, setClassifyStatus] = useState<LibraryClassifyStatus | null>(null);
  const [applyingDefaults, setApplyingDefaults] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const libraryId = Number(id);

  const loadLibrary = useCallback(async () => {
    if (!libraryId) return;
    const [libraryRes, statsRes] = await Promise.all([getLibrary(libraryId), getLibraryStats(libraryId)]);
    if (libraryRes.code === 200) {
      setPlugins(Array.isArray(libraryRes.data?.plugins) ? libraryRes.data.plugins : []);
      setStats((prev) => ({
        ...prev,
        libraryName: libraryRes.data?.name,
        libraryType: libraryRes.data?.type,
      }));
    }
    if (statsRes.code === 200) {
      setStats((prev) => ({ ...prev, ...statsRes.data }));
    }
  }, [libraryId]);

  useEffect(() => {
    loadLibrary();
  }, [loadLibrary]);

  useEffect(() => {
    actionRef.current?.reloadAndRest?.();
  }, [searchQuery]);

  const pollClassifyStatus = useCallback(async () => {
    const res = await getLibraryClassifyStatus();
    if (res.code !== 200 || !res.data) return;
    setClassifyStatus(res.data);
    const runningForThis = res.data.running && res.data.libraryId === libraryId;
    if (runningForThis) {
      window.setTimeout(pollClassifyStatus, 2000);
    } else {
      setClassifying(false);
    }
  }, [libraryId]);

  useEffect(() => {
    if (classifying) {
      pollClassifyStatus();
    }
  }, [classifying, pollClassifyStatus]);

  const handleScrape = async () => {
    if (libraryTypeNeedsScraper(stats?.libraryType) && !hasEnabledScraper(plugins)) {
      message.warning('未启用刮削器插件，请先在插件配置中启用 TMDb 等 SCRAPER。');
      return;
    }
    setScraping(true);
    try {
      const res = await createScrapeTask({ libraryId, targetStatus: 'UNIDENTIFIED' });
      if (res.code === 200) {
        message.success(`刮削任务已创建 #${res.data?.id ?? ''}`);
      }
    } catch {
      message.error('启动刮削失败');
    } finally {
      setScraping(false);
    }
  };

  const handleClassifyLibrary = async () => {
    setClassifying(true);
    try {
      const res = await classifyLibrary(libraryId);
      if (res.code === 200) {
        if (res.data?.accepted === false) {
          message.info(res.data?.message || '库级分类任务正在进行中');
          setClassifyStatus(res.data?.status ?? null);
        } else {
          message.success(res.data?.message || '库级分类任务已启动');
        }
        pollClassifyStatus();
      }
    } catch {
      message.error('启动分类失败');
      setClassifying(false);
    }
  };

  const handleApplyDefaults = async () => {
    setApplyingDefaults(true);
    try {
      const res = await applyDefaultLibraryPlugins(libraryId);
      if (res.code === 200) {
        message.success('已恢复为当前库类型的默认插件链');
        await loadLibrary();
      }
    } finally {
      setApplyingDefaults(false);
    }
  };

  const showScraperWarning = libraryTypeNeedsScraper(stats?.libraryType) && !hasEnabledScraper(plugins);
  const classifyRunning = classifyStatus?.running && classifyStatus?.libraryId === libraryId;

  const columns: ProColumns<MediaItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '标题',
      dataIndex: 'title',
      render: (dom, entity) => <a onClick={() => history.push(`/media/${entity.id}`)}>{dom}</a>,
    },
    { title: '类型', dataIndex: 'type', width: 100 },
    { title: '状态', dataIndex: 'status', width: 120 },
    { title: '发行日期', dataIndex: 'releaseDate', valueType: 'date', width: 120 },
  ];

  return (
    <PageContainer
      title={stats?.libraryName ? `${stats.libraryName}` : `媒体库 ${id}`}
      extra={
        <Space wrap>
          <Button onClick={() => history.push(`/browse?libraryId=${id}`)}>浏览全部媒体</Button>
          {access.canEditLibraryPlugins && (
            <>
              <Button onClick={() => history.push(`/libraries/${id}/plugins`)}>插件配置</Button>
              <Button loading={applyingDefaults} onClick={handleApplyDefaults}>
                恢复默认插件链
              </Button>
            </>
          )}
          {access.canManageLibrary && <Button onClick={() => history.push(`/libraries/${id}/edit`)}>编辑</Button>}
          {access.canScanLibrary && (
            <Button type="primary" onClick={() => setScanModalOpen(true)}>
              扫描库
            </Button>
          )}
          {access.canManageLibrary && <Button loading={scraping} onClick={handleScrape}>刮削此库</Button>}
          {access.canEditMetadata && (
            <Button loading={classifying || !!classifyRunning} onClick={handleClassifyLibrary}>
              重新分类
            </Button>
          )}
        </Space>
      }
    >
      {showScraperWarning && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="未配置刮削器"
          description="在线刮削需要启用 SCRAPER 插件，例如 TMDb。请打开插件配置或恢复默认插件链。"
          action={
            <Button size="small" onClick={() => history.push(`/libraries/${id}/plugins`)}>
              插件配置
            </Button>
          }
        />
      )}
      {classifyRunning && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="库级分类进行中"
          description={
            <Space direction="vertical" style={{ width: '100%' }}>
              <span>
                已处理 {classifyStatus?.processed ?? 0} 项
                {(classifyStatus?.failed ?? 0) > 0 ? `，失败 ${classifyStatus?.failed}` : ''}
              </span>
              <Progress percent={100} status="active" showInfo={false} size="small" />
            </Space>
          }
        />
      )}
      {stats && (
        <Card style={{ marginBottom: 16 }}>
          <Space size="large" wrap>
            <Statistic title="可见媒体项" value={stats.totalItems ?? 0} />
            <Statistic title="类型" value={stats.libraryType ?? '-'} />
          </Space>
          {plugins.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <span style={{ color: 'rgba(255,255,255,0.45)', marginRight: 8 }}>已配置插件：</span>
              {plugins
                .filter((plugin) => plugin.enabled !== false)
                .map((plugin) => (
                  <Tag key={`${plugin.pluginId}-${plugin.kind}`}>
                    {plugin.pluginId} / {pluginKindLabel(plugin.kind)}
                  </Tag>
                ))}
            </div>
          )}
        </Card>
      )}
      <div style={{ marginBottom: 16 }}>
        <UnifiedSearchBox
          value={searchValue}
          onChange={setSearchValue}
          onSearch={(nextQuery) => setSearchQuery(nextQuery)}
          onClear={() => {
            setSearchValue('');
            setSearchQuery('');
          }}
          libraryId={libraryId}
          placeholder={`搜索 ${stats?.libraryName || '此媒体库'} 内的媒体`}
          size="middle"
          style={{ maxWidth: 640 }}
        />
      </div>
      <ProTable<MediaItem>
        actionRef={actionRef}
        columns={columns}
        rowKey="id"
        request={async (params) => {
          const current = params.current || 1;
          const pageSize = params.pageSize || 20;
          if (searchQuery.trim()) {
            const res = await searchUnified({
              query: searchQuery.trim(),
              libraryId,
              page: current,
              size: pageSize,
            });
            return {
              data: res?.data?.results?.items || [],
              success: res?.code === 200,
              total: res?.data?.results?.total || 0,
            };
          }
          const res = await getItems({
            libraryId,
            page: current,
            size: pageSize,
          });
          return {
            data: res?.data?.items || [],
            success: res?.code === 200,
            total: res?.data?.total || 0,
          };
        }}
        search={false}
      />

      <LibraryScanModal
        open={scanModalOpen}
        libraryId={libraryId}
        libraryName={stats?.libraryName}
        onClose={() => setScanModalOpen(false)}
      />
    </PageContainer>
  );
};

export default LibraryDetail;
