import React, { useMemo } from 'react';
import { ReactWordcloud, type Word } from '@cp949/react-wordcloud';
import { Empty, Spin, Typography } from 'antd';
import { history } from '@umijs/max';
import type { TagItem } from '@/services/classification';
import './index.css';

const DEFAULT_COLOR = '#5b9dff';
const WORD_CLOUD_FONT =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif";

interface TagWordCloudProps {
  tags: TagItem[];
  loading?: boolean;
}

type TagWord = Word & {
  tagId: number;
  color?: string;
  usageCount: number;
};

function ensureVisibleColor(color?: string): string {
  if (!color) {
    return DEFAULT_COLOR;
  }

  const normalized = color.trim();
  const hexMatch = /^#?([0-9a-f]{6})$/i.exec(normalized);
  if (!hexMatch) {
    return normalized;
  }

  const hex = hexMatch[1];
  const r = Number.parseInt(hex.slice(0, 2), 16);
  const g = Number.parseInt(hex.slice(2, 4), 16);
  const b = Number.parseInt(hex.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

  if (luminance >= 0.42) {
    return `#${hex}`;
  }

  const brighten = (channel: number) =>
    Math.min(255, Math.round(channel + (255 - channel) * 0.5));
  const toHex = (channel: number) => brighten(channel).toString(16).padStart(2, '0');

  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function buildLayout(tagCount: number) {
  if (tagCount <= 30) {
    return { fontSizes: [16, 48] as [number, number], padding: 5, height: 460 };
  }
  if (tagCount <= 80) {
    return { fontSizes: [14, 36] as [number, number], padding: 7, height: 620 };
  }
  if (tagCount <= 150) {
    return { fontSizes: [12, 28] as [number, number], padding: 9, height: 760 };
  }
  return { fontSizes: [11, 22] as [number, number], padding: 11, height: 900 };
}

const TagWordCloud: React.FC<TagWordCloudProps> = ({ tags, loading }) => {
  const words = useMemo<TagWord[]>(
    () =>
      tags.map((tag) => ({
        text: tag.name,
        value: Math.max(tag.usageCount ?? 0, 1),
        tagId: tag.id,
        color: tag.color,
        usageCount: tag.usageCount ?? 0,
      })),
    [tags],
  );

  const layout = useMemo(() => buildLayout(words.length), [words.length]);

  const callbacks = useMemo(
    () => ({
      getWordColor: (word: Word) => ensureVisibleColor((word as TagWord).color),
      getWordTooltip: (word: Word) => {
        const tagWord = word as TagWord;
        return `${tagWord.text} · 关联 ${tagWord.usageCount} 项媒体`;
      },
      onWordClick: (word: Word) => {
        history.push(`/browse?tagIds=${(word as TagWord).tagId}`);
      },
    }),
    [],
  );

  const options = useMemo(
    () => ({
      deterministic: true,
      randomSeed: 'media-manager-tags',
      enableTooltip: true,
      fontSizes: layout.fontSizes,
      rotations: 1,
      rotationAngles: [0, 0] as [number, number],
      padding: layout.padding,
      scale: 'linear' as const,
      spiral: 'rectangular' as const,
      fontFamily: WORD_CLOUD_FONT,
      fontWeight: '600',
      transitionDuration: 0,
    }),
    [layout.fontSizes, layout.padding],
  );

  if (loading) {
    return (
      <div className="tag-wordcloud">
        <Spin />
      </div>
    );
  }

  if (words.length === 0) {
    return (
      <div className="tag-wordcloud tag-wordcloud--empty">
        <Empty description="暂无标签" />
      </div>
    );
  }

  return (
    <div className="tag-wordcloud" style={{ minHeight: layout.height }}>
      {words.length > 80 && (
        <Typography.Text type="secondary" className="tag-wordcloud-hint">
          共 {words.length} 个标签，已自动缩小字号以尽量完整展示
        </Typography.Text>
      )}
      <ReactWordcloud
        words={words}
        maxWords={words.length}
        minSize={[720, layout.height - 40]}
        style={{ width: '100%', height: layout.height - 40 }}
        callbacks={callbacks}
        options={options}
      />
    </div>
  );
};

export default TagWordCloud;
