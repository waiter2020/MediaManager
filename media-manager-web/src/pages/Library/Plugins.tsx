import React, { useCallback, useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Card,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  CloudSyncOutlined,
  ControlOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { history, useAccess, useParams } from '@umijs/max';
import AiProviderConfigForm from '@/components/AiProviderConfigForm';
import PluginExtractorConfigForm from '@/components/PluginExtractorConfigForm';
import { defaultLibraryAiConfig } from '@/services/ai';
import { getLibrary } from '@/services/library';
import {
  applyDefaultLibraryPlugins,
  listLibraryPlugins,
  listPlugins,
  updateLibraryPlugins,
  type PluginCatalogItem,
} from '@/services/plugin';
import { getIntegrationsSettings } from '@/services/settings';
import { getSystemCapabilities, type SystemCapabilities } from '@/services/system';
import type { LibraryPluginConfig } from '@/types/library';
import { hasEnabledScraper, libraryTypeNeedsScraper, pluginKindLabel } from '@/utils/pluginLabels';

interface PluginRow extends LibraryPluginConfig {
  key: string;
  displayName?: string;
}

interface CatalogRow {
  id: string;
  kind: string;
  displayName: string;
}

const SCRAPER_PLUGIN_IDS = ['tmdb', 'javbus', 'stashdb'];

const FALLBACK_CATALOG: CatalogRow[] = [
  { id: 'nfo', kind: 'EXTRACTOR', displayName: 'NFO' },
  { id: 'ffprobe', kind: 'EXTRACTOR', displayName: 'FFPROBE' },
  { id: 'exif', kind: 'EXTRACTOR', displayName: 'EXIF' },
  { id: 'mock', kind: 'EXTRACTOR', displayName: 'MOCK' },
  { id: 'tmdb', kind: 'SCRAPER', displayName: 'TMDB' },
  { id: 'javbus', kind: 'SCRAPER', displayName: 'JAVBUS' },
  { id: 'stashdb', kind: 'SCRAPER', displayName: 'STASHDB' },
  { id: 'ollama', kind: 'AI_PROVIDER', displayName: 'Ollama (Local)' },
  { id: 'openai-compatible', kind: 'AI_PROVIDER', displayName: 'OpenAI Compatible API' },
];

function normalizePluginId(pluginId?: string) {
  return (pluginId || '').trim().toLowerCase();
}

function normalizePluginKind(pluginId: string, kind: string) {
  return SCRAPER_PLUGIN_IDS.includes(normalizePluginId(pluginId))
    ? 'SCRAPER'
    : (kind || 'EXTRACTOR').trim().toUpperCase();
}

function normalizeCatalog(item: PluginCatalogItem): CatalogRow {
  const id = normalizePluginId(item.pluginId || item.id);
  return {
    id,
    kind: normalizePluginKind(id, item.kind || 'EXTRACTOR'),
    displayName: item.displayName || item.name || id,
  };
}

function catalogFromConfigured(configs: LibraryPluginConfig[]): CatalogRow[] {
  return configs
    .filter((config) => Boolean(config.pluginId))
    .map((config) => {
      const id = normalizePluginId(config.pluginId);
      return {
        id,
        kind: normalizePluginKind(id, config.kind || 'EXTRACTOR'),
        displayName: id.toUpperCase(),
      };
    });
}

function mergeCatalogs(...lists: CatalogRow[][]): CatalogRow[] {
  const byKey = new Map<string, CatalogRow>();
  lists.flat().forEach((plugin) => {
    if (!plugin.id) return;
    const id = normalizePluginId(plugin.id);
    const kind = normalizePluginKind(id, plugin.kind || 'EXTRACTOR');
    const key = `${kind}:${id}`;
    if (!byKey.has(key)) {
      byKey.set(key, { ...plugin, id, kind });
    }
  });
  return Array.from(byKey.values());
}

async function settle<T>(request: Promise<T>): Promise<T | undefined> {
  try {
    return await request;
  } catch {
    return undefined;
  }
}

function hasApiKeyConfig(config?: string) {
  if (!config?.trim()) return false;
  try {
    const parsed = JSON.parse(config);
    return Boolean(parsed?.apiKey || parsed?.api_key);
  } catch {
    return false;
  }
}

const kindColor = (kind: string) => {
  switch (kind) {
    case 'EXTRACTOR':
      return 'blue';
    case 'SCRAPER':
      return 'purple';
    case 'AI_PROVIDER':
      return 'cyan';
    case 'CLASSIFIER':
      return 'green';
    default:
      return 'default';
  }
};

const LibraryPlugins: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const access = useAccess();
  const libraryId = Number(id);
  const [libraryName, setLibraryName] = useState('');
  const [libraryType, setLibraryType] = useState('');
  const [rows, setRows] = useState<PluginRow[]>([]);
  const [catalog, setCatalog] = useState<CatalogRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [tmdbConfigured, setTmdbConfigured] = useState<boolean | undefined>();
  const [capabilities, setCapabilities] = useState<SystemCapabilities>();
  const [catalogFallback, setCatalogFallback] = useState(false);

  const scraperRows = rows.filter((row) => row.kind === 'SCRAPER' && row.enabled !== false);
  const tmdbRows = scraperRows.filter((row) => row.pluginId?.toLowerCase() === 'tmdb');
  const tmdbReady =
    tmdbRows.length === 0 || tmdbConfigured === true || tmdbRows.some((row) => hasApiKeyConfig(row.config));
  const ffprobeEnabled = rows.some(
    (row) => row.pluginId?.toLowerCase() === 'ffprobe' && row.enabled !== false,
  );

  const load = useCallback(async () => {
    if (!libraryId) return;
    setLoading(true);
    try {
      const [libRes, catRes, cfgRes] = await Promise.all([
        settle(getLibrary(libraryId)),
        settle(listPlugins()),
        settle(listLibraryPlugins(libraryId)),
      ]);

      if (libRes?.code === 200) {
        setLibraryName(libRes.data?.name || '');
        setLibraryType(libRes.data?.type || '');
      }

      const configLoaded = cfgRes?.code === 200;
      const rawConfigs = configLoaded ? cfgRes.data || [] : [];
      if (!configLoaded) {
        message.error(cfgRes?.message || '插件配置加载失败，请检查登录状态或库权限');
      }

      const catalogList = mergeCatalogs(
        catRes?.code === 200 ? (catRes.data || []).map(normalizeCatalog) : [],
        catalogFromConfigured(rawConfigs),
        FALLBACK_CATALOG,
      );
      setCatalogFallback(!catRes || catRes.code !== 200);
      setCatalog(catalogList);

      const configured =
        rawConfigs.map<PluginRow>((config, index) => {
          const pluginId = normalizePluginId(config.pluginId);
          const kind = normalizePluginKind(pluginId, config.kind || 'EXTRACTOR');
          return {
            ...config,
            pluginId,
            kind,
            key: `${pluginId}-${kind}-${index}`,
            enabled: config.enabled !== false,
            priority: config.priority ?? 100,
            config: config.config || '',
            displayName: catalogList.find((plugin) => plugin.id === pluginId && plugin.kind === kind)?.displayName,
          };
        }) || [];

      setRows(
        !configLoaded
          ? []
          : configured.length > 0
          ? configured
          : catalogList.map((plugin, index) => ({
              key: `new-${plugin.id}-${index}`,
              pluginId: plugin.id,
              kind: plugin.kind,
              displayName: plugin.displayName,
              enabled: true,
              priority: index * 10,
              config: plugin.kind === 'AI_PROVIDER' ? defaultLibraryAiConfig(plugin.id) : '',
            })),
      );

      if (access.canManageSystem) {
        const [capRes, integrationsRes] = await Promise.all([
          settle(getSystemCapabilities()),
          settle(getIntegrationsSettings()),
        ]);
        if (capRes?.code === 200) {
          setCapabilities(capRes.data);
        }
        if (integrationsRes?.code === 200) {
          setTmdbConfigured(Boolean(integrationsRes.data?.tmdbApiKeyConfigured));
        }
      }
    } finally {
      setLoading(false);
    }
  }, [access.canManageSystem, libraryId]);

  useEffect(() => {
    load();
  }, [load]);

  const normalizedKind = normalizePluginKind;

  const rowIdentity = (pluginId: string, kind: string) =>
    `${normalizedKind(pluginId, kind).toUpperCase()}:${pluginId?.toLowerCase()}`;

  const hasDuplicate = (rowKey: string | undefined, pluginId: string, kind: string) =>
    rows.some((row) => row.key !== rowKey && rowIdentity(row.pluginId, row.kind) === rowIdentity(pluginId, kind));

  const catalogOptionsForRow = (row: PluginRow) =>
    catalog.map((plugin) => ({
      value: plugin.id,
      label: `${plugin.displayName} · ${pluginKindLabel(plugin.kind)}`,
      disabled: hasDuplicate(row.key, plugin.id, plugin.kind),
    }));

  const patchRow = (key: string, patch: Partial<PluginRow>) => {
    setRows((prev) => {
      return prev.map((row) => (row.key === key ? { ...row, ...patch } : row));
    });
  };

  const addRow = (preferKind?: string) => {
    const candidates = preferKind ? catalog.filter((plugin) => plugin.kind === preferKind) : catalog;
    const pick = candidates.find((plugin) => !hasDuplicate(undefined, plugin.id, plugin.kind));
    if (!pick) {
      message.warning(preferKind ? `没有可继续添加的${pluginKindLabel(preferKind)}` : '没有可继续添加的插件');
      return;
    }
    setRows((prev) => [
      ...prev,
      {
        key: `new-${Date.now()}`,
        pluginId: pick.id,
        kind: normalizedKind(pick.id, pick.kind),
        displayName: pick.displayName,
        enabled: true,
        priority: prev.length * 10,
        config: pick.kind === 'AI_PROVIDER' ? defaultLibraryAiConfig(pick.id) : '',
      },
    ]);
  };

  const normalizePriorities = () => {
    setRows((prev) =>
      [...prev]
        .sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100))
        .map((row, index) => ({ ...row, priority: index * 10 })),
    );
  };

  const save = async () => {
    const identities = new Set<string>();
    for (const row of rows) {
      const identity = rowIdentity(row.pluginId, row.kind);
      if (identities.has(identity)) {
        message.error(`${row.pluginId} 已经配置过`);
        return;
      }
      identities.add(identity);
    }
    for (const row of rows) {
      if (row.config?.trim()) {
        try {
          JSON.parse(row.config);
        } catch {
          message.error(`${row.pluginId} 的配置不是有效 JSON`);
          return;
        }
      }
    }
    setSaving(true);
    try {
      const payload: LibraryPluginConfig[] = rows.map(({ pluginId, kind, enabled, priority, config }) => ({
        pluginId: pluginId.toLowerCase(),
        kind: normalizedKind(pluginId, kind).toUpperCase(),
        enabled,
        priority,
        config: config?.trim() || '{}',
      }));
      const res = await updateLibraryPlugins(libraryId, payload);
      if (res.code === 200) {
        message.success('插件配置已保存');
        load();
      } else {
        message.error(res.message || '保存失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<PluginRow> = [
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 78,
      fixed: 'left',
      render: (enabled: boolean, row) => (
        <Switch checked={enabled} onChange={(checked) => patchRow(row.key, { enabled: checked })} />
      ),
    },
    {
      title: '类型',
      dataIndex: 'kind',
      width: 110,
      render: (kind: string) => <Tag color={kindColor(kind)}>{pluginKindLabel(kind)}</Tag>,
    },
    {
      title: '插件',
      dataIndex: 'pluginId',
      width: 260,
      render: (_value, row) => (
        <Select
          style={{ width: 230 }}
          value={row.pluginId}
          options={catalogOptionsForRow(row)}
          showSearch
          optionFilterProp="label"
          onChange={(value) => {
            const plugin = catalog.find((item) => item.id === value);
            const kind = normalizedKind(value, plugin?.kind || row.kind);
            if (hasDuplicate(row.key, value, kind)) {
              message.warning(`${value} 已经配置过`);
              return;
            }
            patchRow(row.key, {
              pluginId: value,
              kind,
              displayName: plugin?.displayName,
              config: kind === 'AI_PROVIDER' && !row.config?.trim() ? defaultLibraryAiConfig(value) : row.config,
            });
          }}
        />
      ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 110,
      render: (priority: number, row) => (
        <InputNumber min={0} value={priority} onChange={(value) => patchRow(row.key, { priority: value ?? 0 })} />
      ),
    },
    {
      title: '配置',
      dataIndex: 'config',
      render: (value: string | undefined, row) =>
        row.kind === 'AI_PROVIDER' ? (
          <AiProviderConfigForm
            providerId={row.pluginId}
            value={value}
            onChange={(json) => patchRow(row.key, { config: json })}
          />
        ) : row.kind === 'EXTRACTOR' || row.kind === 'SCRAPER' ? (
          <PluginExtractorConfigForm
            pluginId={row.pluginId}
            value={value}
            onChange={(json) => patchRow(row.key, { config: json })}
          />
        ) : (
          <Input.TextArea
            rows={2}
            value={value}
            placeholder='{"key":"value"}'
            onChange={(event) => patchRow(row.key, { config: event.target.value })}
          />
        ),
    },
    {
      title: '操作',
      width: 80,
      render: (_value, row) => (
        <Button type="link" danger onClick={() => setRows((prev) => prev.filter((item) => item.key !== row.key))}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <PageContainer
      title={`插件配置 - ${libraryName || libraryId}`}
      subTitle="本地提取、远程刮削和库级覆盖"
      onBack={() => history.push(`/libraries/${libraryId}`)}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save}>
            保存
          </Button>
        </Space>
      }
    >
      {catalogFallback ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="插件注册表暂时不可用"
          description="当前页面已使用内置插件候选兜底，仍可编辑和保存本库插件配置。"
        />
      ) : null}
      {libraryTypeNeedsScraper(libraryType) && !hasEnabledScraper(rows) ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="未启用刮削器"
          description="电影、剧集和混合库需要启用 TMDb、JavBus 或 StashDB 等 SCRAPER。"
        />
      ) : null}
      {!tmdbReady ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="TMDb 缺少 API Key"
          description="可以在系统集成中设置全局 Key，也可以在本库的 TMDb 配置中填写覆盖值。"
          action={
            access.canManageSystem ? (
              <Button size="small" onClick={() => history.push('/settings/integrations')}>
                集成设置
              </Button>
            ) : undefined
          }
        />
      ) : null}
      {ffprobeEnabled && capabilities?.ffprobeAvailable === false ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="FFprobe 当前不可用"
          description="FFprobe 提取器已启用，但系统检测不到可执行文件。"
          action={
            access.canManageSystem ? (
              <Button size="small" onClick={() => history.push('/settings/media-processing')}>
                媒体处理
              </Button>
            ) : undefined
          }
        />
      ) : null}

      <Card loading={loading}>
        <Space style={{ marginBottom: 16 }} wrap>
          <Tag icon={<ControlOutlined />} color="blue">
            提取器 {rows.filter((row) => row.kind === 'EXTRACTOR' && row.enabled !== false).length}
          </Tag>
          <Tag icon={<CloudSyncOutlined />} color="purple">
            刮削器 {scraperRows.length}
          </Tag>
          <Tag icon={<ApiOutlined />} color={tmdbReady ? 'success' : 'warning'}>
            TMDb {tmdbReady ? '就绪' : '待配置'}
          </Tag>
          <Button onClick={() => addRow('EXTRACTOR')}>添加提取器</Button>
          <Button onClick={() => addRow('SCRAPER')}>添加刮削器</Button>
          <Button onClick={() => addRow('AI_PROVIDER')}>添加 AI</Button>
          <Button onClick={normalizePriorities}>整理优先级</Button>
          <Button
            onClick={async () => {
              const res = await applyDefaultLibraryPlugins(libraryId);
              if (res.code === 200) {
                message.success('已恢复为当前库类型的推荐链');
                load();
              } else {
                message.error(res.message || '恢复失败');
              }
            }}
          >
            恢复推荐链
          </Button>
          <Button type="link" onClick={() => history.push('/settings/tasks')}>
            任务监控
          </Button>
        </Space>
        <Table<PluginRow>
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={[...rows].sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100))}
          columns={columns}
          scroll={{ x: 1080 }}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
          扫描只运行 EXTRACTOR；刮削任务会按优先级运行 EXTRACTOR 与 SCRAPER。
        </Typography.Paragraph>
      </Card>
    </PageContainer>
  );
};

export default LibraryPlugins;
