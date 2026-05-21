import React, { useCallback, useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, message, Select, Space, Switch, Table, InputNumber } from 'antd';
import { history, useParams } from '@umijs/max';
import { listLibraryPlugins, listPlugins, updateLibraryPlugins } from '@/services/plugin';
import { getLibrary } from '@/services/library';

interface PluginRow {
  key: string;
  pluginId: string;
  kind: string;
  displayName?: string;
  enabled: boolean;
  priority: number;
  config: string;
}

const LibraryPlugins: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const libraryId = Number(id);
  const [libraryName, setLibraryName] = useState('');
  const [rows, setRows] = useState<PluginRow[]>([]);
  const [catalog, setCatalog] = useState<{ id: string; kind: string; displayName: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

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
      }
      const catalogList =
        catRes.code === 200
          ? (catRes.data || []).map((p: any) => ({
              id: String(p.id),
              kind: String(p.kind),
              displayName: String(p.displayName || p.id),
            }))
          : [];
      setCatalog(catalogList);

      const configured: PluginRow[] =
        cfgRes.code === 200
          ? (cfgRes.data || []).map((c: any, idx: number) => ({
              key: `cfg-${idx}`,
              pluginId: c.pluginId,
              kind: c.kind,
              enabled: c.enabled !== false,
              priority: c.priority ?? 100,
              config: c.config || '',
            }))
          : [];

      if (configured.length > 0) {
        setRows(configured);
      } else if (catalogList.length > 0) {
        setRows(
          catalogList.map((p, idx) => ({
            key: `new-${idx}`,
            pluginId: p.id,
            kind: p.kind,
            displayName: p.displayName,
            enabled: true,
            priority: idx * 10,
            config: '',
          })),
        );
      } else {
        setRows([]);
      }
    } finally {
      setLoading(false);
    }
  }, [libraryId]);

  useEffect(() => {
    load();
  }, [load]);

  const addRow = () => {
    const first = catalog[0];
    if (!first) {
      message.warning('无可用插件');
      return;
    }
    setRows((prev) => [
      ...prev,
      {
        key: `new-${Date.now()}`,
        pluginId: first.id,
        kind: first.kind,
        displayName: first.displayName,
        enabled: true,
        priority: prev.length * 10,
        config: '',
      },
    ]);
  };

  const save = async () => {
    setSaving(true);
    try {
      const payload = rows.map((r) => ({
        pluginId: r.pluginId,
        kind: r.kind,
        enabled: r.enabled,
        priority: r.priority,
        config: r.config,
      }));
      const res = await updateLibraryPlugins(libraryId, payload);
      if (res.code === 200) {
        message.success('插件配置已保存');
        load();
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: '插件',
      dataIndex: 'pluginId',
      render: (_: unknown, row: PluginRow, index: number) => (
        <Select
          style={{ width: 200 }}
          value={row.pluginId}
          options={catalog.map((p) => ({
            value: p.id,
            label: `${p.displayName} (${p.kind})`,
          }))}
          onChange={(v) => {
            const p = catalog.find((c) => c.id === v);
            setRows((prev) => {
              const next = [...prev];
              next[index] = {
                ...next[index],
                pluginId: v,
                kind: p?.kind || next[index].kind,
              };
              return next;
            });
          }}
        />
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (v: boolean, _row: PluginRow, index: number) => (
        <Switch
          checked={v}
          onChange={(checked) =>
            setRows((prev) => {
              const next = [...prev];
              next[index] = { ...next[index], enabled: checked };
              return next;
            })
          }
        />
      ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 100,
      render: (v: number, _row: PluginRow, index: number) => (
        <InputNumber
          min={0}
          value={v}
          onChange={(n) =>
            setRows((prev) => {
              const next = [...prev];
              next[index] = { ...next[index], priority: n ?? 0 };
              return next;
            })
          }
        />
      ),
    },
    {
      title: '操作',
      width: 80,
      render: (_: unknown, row: PluginRow) => (
        <Button type="link" danger onClick={() => setRows((prev) => prev.filter((r) => r.key !== row.key))}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <PageContainer
      title={`插件配置 · ${libraryName || libraryId}`}
      onBack={() => history.push(`/libraries/${libraryId}`)}
    >
      <Card loading={loading}>
        <Space style={{ marginBottom: 16 }}>
          <Button onClick={addRow}>添加插件</Button>
          <Button type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </Space>
        <Table rowKey="key" size="small" pagination={false} dataSource={rows} columns={columns} />
      </Card>
    </PageContainer>
  );
};

export default LibraryPlugins;
