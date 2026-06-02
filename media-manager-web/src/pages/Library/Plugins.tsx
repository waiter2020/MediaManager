import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Alert, Button, Card, Input, InputNumber, message, Select, Space, Switch, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { history, useParams } from '@umijs/max';
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

function normalizeCatalog(item: PluginCatalogItem): CatalogRow {
  const id = item.pluginId || item.id;
  return {
    id,
    kind: item.kind,
    displayName: item.displayName || item.name || id,
  };
}

const LibraryPlugins: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const libraryId = Number(id);
  const [libraryName, setLibraryName] = useState('');
  const [libraryType, setLibraryType] = useState('');
  const [rows, setRows] = useState<PluginRow[]>([]);
  const [catalog, setCatalog] = useState<CatalogRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const catalogOptions = useMemo(
    () =>
      catalog.map((plugin) => ({
        value: plugin.id,
        label: `${plugin.displayName} (${plugin.kind})`,
      })),
    [catalog],
  );

  const load = useCallback(async () => {
    if (!libraryId) return;
    setLoading(true);
    try {
      const [libRes, catRes, cfgRes] = await Promise.all([
        getLibrary(libraryId),
        listPlugins(),
        listLibraryPlugins(libraryId),
      ]);

      if (libRes.code === 200) {
        setLibraryName(libRes.data?.name || '');
        setLibraryType(libRes.data?.type || '');
      }

      const catalogList = catRes.code === 200 ? (catRes.data || []).map(normalizeCatalog) : [];
      setCatalog(catalogList);

      const configured =
        cfgRes.code === 200
          ? (cfgRes.data || []).map<PluginRow>((config, index) => ({
              ...config,
              key: `${config.pluginId}-${config.kind}-${index}`,
              enabled: config.enabled !== false,
              priority: config.priority ?? 100,
              config: config.config || '',
              displayName: catalogList.find((plugin) => plugin.id === config.pluginId)?.displayName,
            }))
          : [];

      setRows(
        configured.length > 0
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
    } finally {
      setLoading(false);
    }
  }, [libraryId]);

  useEffect(() => {
    load();
  }, [load]);

  const patchRow = (index: number, patch: Partial<PluginRow>) => {
    setRows((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], ...patch };
      return next;
    });
  };

  const addRow = (preferKind?: string) => {
    const pick = (preferKind ? catalog.find((plugin) => plugin.kind === preferKind) : undefined) || catalog[0];
    if (!pick) {
      message.warning('暂无可用插件');
      return;
    }
    setRows((prev) => [
      ...prev,
      {
        key: `new-${Date.now()}`,
        pluginId: pick.id,
        kind: pick.kind,
        displayName: pick.displayName,
        enabled: true,
        priority: prev.length * 10,
        config: pick.kind === 'AI_PROVIDER' ? defaultLibraryAiConfig(pick.id) : '',
      },
    ]);
  };

  const save = async () => {
    setSaving(true);
    try {
      const payload: LibraryPluginConfig[] = rows.map(({ pluginId, kind, enabled, priority, config }) => ({
        pluginId,
        kind,
        enabled,
        priority,
        config,
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
      title: '类型',
      dataIndex: 'kind',
      width: 100,
      render: (kind: string) => pluginKindLabel(kind),
    },
    {
      title: '插件',
      dataIndex: 'pluginId',
      width: 260,
      render: (_value, row, index) => (
        <Select
          style={{ width: 220 }}
          value={row.pluginId}
          options={catalogOptions}
          onChange={(value) => {
            const plugin = catalog.find((item) => item.id === value);
            const kind = plugin?.kind || row.kind;
            patchRow(index, {
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
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean, _row, index) => (
        <Switch checked={enabled} onChange={(checked) => patchRow(index, { enabled: checked })} />
      ),
    },
    {
      title: '配置',
      dataIndex: 'config',
      render: (value: string | undefined, row, index) =>
        row.kind === 'AI_PROVIDER' ? (
          <AiProviderConfigForm providerId={row.pluginId} value={value} onChange={(json) => patchRow(index, { config: json })} />
        ) : row.kind === 'EXTRACTOR' || row.kind === 'SCRAPER' ? (
          <PluginExtractorConfigForm
            pluginId={row.pluginId}
            value={value}
            onChange={(json) => patchRow(index, { config: json })}
          />
        ) : (
          <Input.TextArea
            rows={2}
            value={value}
            placeholder='{"key":"value"}'
            onChange={(event) => patchRow(index, { config: event.target.value })}
          />
        ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 110,
      render: (priority: number, _row, index) => (
        <InputNumber min={0} value={priority} onChange={(value) => patchRow(index, { priority: value ?? 0 })} />
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
    <PageContainer title={`插件配置 - ${libraryName || libraryId}`} onBack={() => history.push(`/libraries/${libraryId}`)}>
      <Card loading={loading}>
        {libraryTypeNeedsScraper(libraryType) && !hasEnabledScraper(rows) ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="未启用刮削器"
            description="电影、剧集、混合库建议启用 TMDb 等 SCRAPER，否则刮削任务无法拉取在线元数据。"
          />
        ) : null}
        <Space style={{ marginBottom: 16 }} wrap>
          <Button onClick={() => addRow()}>添加插件</Button>
          <Button onClick={() => addRow('AI_PROVIDER')}>添加 AI 提供方</Button>
          <Button
            onClick={async () => {
              const res = await applyDefaultLibraryPlugins(libraryId);
              if (res.code === 200) {
                message.success('已恢复为当前库类型的默认插件链');
                load();
              } else {
                message.error(res.message || '恢复失败');
              }
            }}
          >
            恢复类型默认
          </Button>
          <Button type="link" onClick={() => history.push('/settings/integrations')}>
            全局 TMDb 设置
          </Button>
          <Button type="link" onClick={() => history.push('/settings/ai')}>
            全局 AI 设置
          </Button>
          <Button type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </Space>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          库级 AI 配置会覆盖全局默认配置；优先级最高且已启用的 AI_PROVIDER 生效。
        </Typography.Paragraph>
        <Table<PluginRow> rowKey="key" size="small" pagination={false} dataSource={rows} columns={columns} />
      </Card>
    </PageContainer>
  );
};

export default LibraryPlugins;
