import React, { useEffect, useMemo, useState } from 'react';
import { Button, Select, Table, Typography } from 'antd';
import { PlayCircleFilled } from '@ant-design/icons';
import { history } from '@umijs/max';
import { openPlayerWindow } from '@/utils/playerWindow';

export interface TvEpisode {
  id?: number;
  episodeNumber?: number;
  title?: string;
  overview?: string;
  runtimeMinutes?: number;
  airDate?: string;
  mediaFileId?: number;
  mediaItemId?: number;
}

export interface TvSeason {
  id?: number;
  seasonNumber?: number;
  name?: string;
  overview?: string;
  episodes?: TvEpisode[];
}

interface Props {
  mediaItemId: number;
  seasons: TvSeason[];
  onSync?: () => void;
  syncing?: boolean;
  canSync?: boolean;
}

const TvSeasonPanel: React.FC<Props> = ({ mediaItemId, seasons, onSync, syncing, canSync }) => {
  const sorted = useMemo(
    () => [...seasons].sort((a, b) => (a.seasonNumber ?? 0) - (b.seasonNumber ?? 0)),
    [seasons],
  );
  const [seasonKey, setSeasonKey] = useState('');

  useEffect(() => {
    setSeasonKey(sorted.length > 0 ? String(sorted[0].id ?? sorted[0].seasonNumber ?? 0) : '');
  }, [sorted]);

  const activeSeason = sorted.find((season) => String(season.id ?? season.seasonNumber) === seasonKey);

  const columns = [
    {
      title: '集',
      dataIndex: 'episodeNumber',
      width: 56,
      render: (n: number) => (n != null ? `E${n}` : '-'),
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (title: string) => title || '未命名',
    },
    {
      title: '时长',
      dataIndex: 'runtimeMinutes',
      width: 88,
      render: (minutes: number) => (minutes > 0 ? `${minutes} 分钟` : '-'),
    },
    {
      title: '播出',
      dataIndex: 'airDate',
      width: 110,
    },
    {
      title: '',
      key: 'play',
      width: 88,
      render: (_: unknown, ep: TvEpisode) =>
        ep.mediaItemId != null ? (
          <Button
            type="link"
            size="small"
            icon={<PlayCircleFilled />}
            onClick={() => history.push(`/media/${ep.mediaItemId}`)}
          >
            打开
          </Button>
        ) : ep.mediaFileId != null ? (
          <Button
            type="link"
            size="small"
            icon={<PlayCircleFilled />}
            onClick={() => {
              if (!openPlayerWindow(mediaItemId, { fileId: ep.mediaFileId })) {
                history.push(`/player/${mediaItemId}?fileId=${ep.mediaFileId}`);
              }
            }}
          >
            播放
          </Button>
        ) : (
          <Typography.Text type="secondary">未关联</Typography.Text>
        ),
    },
  ];

  if (sorted.length === 0) {
    return (
      <div style={{ marginTop: 8 }}>
        {canSync && onSync && (
          <Button loading={syncing} onClick={onSync} style={{ marginBottom: 8 }}>
            从 TMDb 同步季集
          </Button>
        )}
        <Typography.Text type="secondary">
          暂无季集数据，请先手动匹配 TMDb 剧集后再同步。
        </Typography.Text>
      </div>
    );
  }

  return (
    <div className="tv-season-panel">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12, flexWrap: 'wrap' }}>
        <Select
          style={{ minWidth: 220 }}
          value={seasonKey}
          onChange={setSeasonKey}
          options={sorted.map((season) => ({
            value: String(season.id ?? season.seasonNumber),
            label: `第 ${season.seasonNumber ?? '?'} 季${season.name ? ` / ${season.name}` : ''}`,
          }))}
        />
        {canSync && onSync && (
          <Button loading={syncing} onClick={onSync}>
            从 TMDb 同步季集
          </Button>
        )}
      </div>
      {activeSeason?.overview && (
        <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ marginBottom: 12 }}>
          {activeSeason.overview}
        </Typography.Paragraph>
      )}
      <Table
        size="small"
        rowKey={(row) => String(row.id ?? row.episodeNumber)}
        pagination={false}
        columns={columns}
        dataSource={activeSeason?.episodes || []}
        locale={{ emptyText: '本季暂无剧集' }}
      />
    </div>
  );
};

export default TvSeasonPanel;
