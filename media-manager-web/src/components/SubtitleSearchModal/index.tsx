import React, { useCallback, useEffect, useState } from 'react';
import { GlobalOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Input, Modal, Select, message } from 'antd';
import {
  downloadSubtitle,
  searchOnlineSubtitles,
  type SubtitleSearchResult,
} from '@/services/media';
import { getSubtitleSettings } from '@/services/settings';
import type { MediaSubtitle } from '@/types/media';
import './index.css';

const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '简体中文 (zh-CN)' },
  { value: 'zh-TW', label: '繁体中文 (zh-TW)' },
  { value: 'en', label: 'English (en)' },
  { value: 'ja', label: '日本語 (ja)' },
  { value: 'ko', label: '한국어 (ko)' },
  { value: 'fr', label: 'Français (fr)' },
  { value: 'de', label: 'Deutsch (de)' },
  { value: 'es', label: 'Español (es)' },
];

export interface SubtitleSearchModalProps {
  itemId: number;
  defaultQuery?: string;
  fileId?: number | null;
  open: boolean;
  onClose: () => void;
  onDownloaded?: (subtitle: MediaSubtitle) => void;
}

const SubtitleSearchModal: React.FC<SubtitleSearchModalProps> = ({
  itemId,
  defaultQuery = '',
  fileId,
  open,
  onClose,
  onDownloaded,
}) => {
  const [query, setQuery] = useState(defaultQuery);
  const [language, setLanguage] = useState('zh-CN');
  const [customLanguage, setCustomLanguage] = useState('');
  const [loading, setLoading] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [results, setResults] = useState<SubtitleSearchResult[]>([]);
  const [providerConfigured, setProviderConfigured] = useState<boolean | null>(null);
  const [searched, setSearched] = useState(false);

  const resolvedLanguage = customLanguage.trim() || language;

  const loadProviderStatus = useCallback(async () => {
    try {
      const res = await getSubtitleSettings();
      if (res.code === 200 && res.data) {
        const configured = res.data.providers?.some((provider) => provider.configured) ?? false;
        setProviderConfigured(configured);
        if (res.data.defaultLanguage) {
          setLanguage(res.data.defaultLanguage);
        }
      }
    } catch {
      setProviderConfigured(null);
    }
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }
    setQuery(defaultQuery);
    setResults([]);
    setSearched(false);
    loadProviderStatus();
  }, [open, defaultQuery, loadProviderStatus]);

  const handleSearch = async () => {
    setLoading(true);
    setSearched(true);
    try {
      const res = await searchOnlineSubtitles(itemId, query || defaultQuery, resolvedLanguage, fileId ?? undefined);
      if (res.code === 200) {
        setResults(res.data || []);
      }
    } catch {
      message.error('字幕搜索失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async (result: SubtitleSearchResult) => {
    if (!result.provider || !result.externalId) {
      message.warning('缺少字幕提供方信息');
      return;
    }
    const key = `${result.provider}-${result.externalId}`;
    setDownloadingId(key);
    try {
      const res = await downloadSubtitle(itemId, {
        provider: result.provider,
        externalId: result.externalId,
        fileId: fileId ?? undefined,
        language: result.language || resolvedLanguage,
      });
      if (res.code === 200 && res.data) {
        message.success('字幕已下载并关联');
        onDownloaded?.(res.data);
        onClose();
      }
    } catch {
      message.error('字幕下载失败，请确认 OpenSubtitles 账号密码已配置');
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <Modal
      title="在线搜索字幕"
      open={open}
      onCancel={onClose}
      footer={null}
      width={720}
      destroyOnClose
    >
      {providerConfigured === false ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="字幕搜索未配置"
          description="请前往 设置 → 集成 配置 OpenSubtitles API Key（下载还需账号密码）。"
        />
      ) : null}
      <div className="subtitle-search-form">
        <Input
          placeholder="搜索关键词"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onPressEnter={handleSearch}
        />
        <Select
          className="subtitle-language-select"
          value={language}
          options={LANGUAGE_OPTIONS}
          onChange={setLanguage}
        />
        <Input
          className="subtitle-language-input"
          placeholder="自定义语言代码"
          value={customLanguage}
          onChange={(event) => setCustomLanguage(event.target.value)}
        />
        <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={handleSearch}>
          搜索
        </Button>
      </div>
      <div className="subtitle-search-results">
        {!searched ? (
          <div className="subtitle-search-empty">输入关键词后搜索在线字幕</div>
        ) : results.length === 0 ? (
          <div className="subtitle-search-empty">
            {providerConfigured === false
              ? '请先配置字幕搜索提供方'
              : '暂无匹配结果，可尝试调整关键词或语言'}
          </div>
        ) : (
          results.map((result) => {
            const key = `${result.provider}-${result.externalId}-${result.title}`;
            const downloading = downloadingId === `${result.provider}-${result.externalId}`;
            return (
              <div key={key} className="subtitle-search-row">
                <GlobalOutlined className="subtitle-search-row-icon" />
                <div className="subtitle-search-row-main">
                  <div className="subtitle-search-row-title">
                    {result.title || result.releaseName || result.externalId}
                  </div>
                  <div className="subtitle-search-row-meta">
                    {result.provider && <span>{result.provider}</span>}
                    {result.language && <span>{result.language}</span>}
                    {result.format && <span>{result.format.toUpperCase()}</span>}
                    {result.score != null && <span>{result.score.toFixed(1)}</span>}
                  </div>
                </div>
                <Button
                  size="small"
                  type="primary"
                  loading={downloading}
                  onClick={() => handleDownload(result)}
                >
                  下载并使用
                </Button>
              </div>
            );
          })
        )}
      </div>
    </Modal>
  );
};

export default SubtitleSearchModal;
