import React, { useEffect, useMemo, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import {
  Button,
  Card,
  Col,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
  Drawer,
  Checkbox,
  List,
  Spin,
  Segmented,
  Slider,
  Switch,
  Tooltip,
  Empty,
} from 'antd';
import { Link, history, useAccess } from '@umijs/max';
import {
  BulbOutlined,
  PercentageOutlined,
  VideoCameraOutlined,
  SettingOutlined,
  TableOutlined,
  AppstoreOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  RobotOutlined,
  SlidersOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import {
  approveSuggestion,
  batchApproveSuggestions,
  batchRejectSuggestions,
  listAiSuggestions,
  rejectSuggestion,
  getAiConfig,
  updateAiConfig,
  type AiSuggestion,
  type AiConfigPayload,
} from '@/services/ai';
import { getItemDetail } from '@/services/media';
import { resolveItemPosterUrl } from '@/services/stream';
import type { MediaItem } from '@/types/media';

const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  overview: '简介',
  originalTitle: '原标题',
  rating: '评分',
  releaseDate: '发行日期',
};

function fieldLabel(fieldName: string): string {
  if (!fieldName) return '-';
  if (fieldName.startsWith('tag:')) {
    const tagName = fieldName.slice(4);
    return tagName ? `标签 / ${tagName}` : '标签';
  }
  return FIELD_LABELS[fieldName] || fieldName;
}

const cardStyle: React.CSSProperties = {
  background: 'linear-gradient(135deg, rgba(20, 20, 35, 0.6) 0%, rgba(10, 10, 15, 0.7) 100%)',
  backdropFilter: 'blur(16px)',
  border: '1px solid rgba(255, 255, 255, 0.08)',
  borderRadius: '16px',
  boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.25)',
  transition: 'all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)',
};

const tableCardStyle: React.CSSProperties = {
  background: 'rgba(20, 20, 32, 0.35)',
  backdropFilter: 'blur(16px)',
  border: '1px solid rgba(255, 255, 255, 0.06)',
  borderRadius: '16px',
  boxShadow: '0 4px 24px rgba(0, 0, 0, 0.15)',
};

const getCurrentValue = (fieldName: string, media: MediaItem | null): string => {
  if (!media) return '加载中...';
  if (fieldName.startsWith('tag:')) {
    const tagName = fieldName.slice(4);
    const hasTag = media.tags?.some((t) => t.name.toLowerCase() === tagName.toLowerCase());
    return hasTag ? '已存在该标签' : '未添加标签';
  }

  if (fieldName === 'title') return media.title || '无';
  if (fieldName === 'originalTitle') return media.originalTitle || '无';
  if (fieldName === 'releaseDate') return media.releaseDate || '无';
  if (fieldName === 'rating') return media.rating !== undefined ? String(media.rating) : '无';
  if (fieldName === 'overview') return media.overview || '无';

  return '-';
};

const getSuggestedValueDisplay = (fieldName: string, suggestedValue?: string) => {
  if (fieldName.startsWith('tag:')) {
    const tagName = fieldName.slice(4);
    return `添加标签: ${tagName}`;
  }
  return suggestedValue || '无';
};

// GROUPED CARD ITEM COMPONENT
const MediaCardItem: React.FC<{
  group: { mediaItemId: number; mediaTitle: string; suggestions: AiSuggestion[] };
  onApproveAll: (ids: number[]) => void;
  onRejectAll: (ids: number[]) => void;
  onCompare: (mediaId: number, media: MediaItem | null) => void;
  selectedRowKeys: React.Key[];
  setSelectedRowKeys: React.Dispatch<React.SetStateAction<React.Key[]>>;
}> = ({ group, onApproveAll, onRejectAll, onCompare, selectedRowKeys, setSelectedRowKeys }) => {
  const [media, setMedia] = useState<MediaItem | null>(null);
  const [mediaLoading, setMediaLoading] = useState(false);

  useEffect(() => {
    let active = true;
    const fetchDetail = async () => {
      setMediaLoading(true);
      try {
        const res = await getItemDetail(group.mediaItemId);
        if (res.code === 200 && active) {
          setMedia(res.data);
        }
      } catch (err) {
        // silent fail
      } finally {
        if (active) setMediaLoading(false);
      }
    };
    fetchDetail();
    return () => {
      active = false;
    };
  }, [group.mediaItemId]);

  const posterUrl = media
    ? resolveItemPosterUrl({
        itemId: media.id,
        posterPath: media.posterPath,
        type: media.type,
        fileIds: media.fileIds,
      })
    : null;

  const suggestionIds = useMemo(() => group.suggestions.map((s) => s.id), [group.suggestions]);
  
  const isAllChecked = useMemo(() => {
    return suggestionIds.every((id) => selectedRowKeys.includes(id));
  }, [suggestionIds, selectedRowKeys]);

  const isSomeChecked = useMemo(() => {
    return suggestionIds.some((id) => selectedRowKeys.includes(id)) && !isAllChecked;
  }, [suggestionIds, selectedRowKeys, isAllChecked]);

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      const toAdd = suggestionIds.filter((id) => !selectedRowKeys.includes(id));
      setSelectedRowKeys((prev) => [...prev, ...toAdd]);
    } else {
      setSelectedRowKeys((prev) => prev.filter((key) => !suggestionIds.includes(Number(key))));
    }
  };

  return (
    <Card
      style={{
        ...cardStyle,
        marginContent: 0,
        overflow: 'hidden',
        border: '1px solid rgba(255, 255, 255, 0.08)',
      }}
      bodyStyle={{ padding: 0 }}
      className="premium-media-card"
    >
      <div style={{ display: 'flex', minHeight: 180, flexWrap: 'wrap' }}>
        {/* POSTER SECTION */}
        <div style={{
          width: 140,
          background: 'rgba(0,0,0,0.2)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          borderRight: '1px solid rgba(255, 255, 255, 0.06)',
          overflow: 'hidden',
        }}>
          {mediaLoading ? (
            <Spin size="small" />
          ) : posterUrl ? (
            <>
              <img
                src={posterUrl}
                alt="poster"
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  position: 'absolute',
                  top: 0,
                  left: 0,
                }}
              />
              <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                background: 'linear-gradient(to bottom, rgba(0,0,0,0.1) 0%, rgba(0,0,0,0.7) 100%)',
              }} />
            </>
          ) : (
            <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.2)' }}>
              <VideoCameraOutlined style={{ fontSize: 32, marginBottom: 8 }} />
              <div style={{ fontSize: 11 }}>无海报</div>
            </div>
          )}
          {media && (
            <Tag
              color="blue"
              style={{
                position: 'absolute',
                top: 8,
                left: 8,
                borderRadius: 4,
                border: 'none',
                backdropFilter: 'blur(8px)',
                background: 'rgba(22, 104, 220, 0.75)',
                fontSize: 10,
                fontWeight: 600,
              }}
            >
              {media.type}
            </Tag>
          )}
        </div>

        {/* DETAILS & SUGGESTIONS SECTION */}
        <div style={{ flex: '1 1 300px', padding: '16px 20px', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div>
              <Link
                to={`/media/${group.mediaItemId}`}
                style={{
                  fontSize: 16,
                  fontWeight: 600,
                  color: '#fff',
                  transition: 'color 0.2s',
                }}
                className="media-card-title-link"
              >
                {group.mediaTitle}
              </Link>
              {media && (
                <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.45)', marginTop: 4 }}>
                  年份: {media.releaseDate ? media.releaseDate.substring(0, 4) : '未知'} | 路径: {media.path || '未知'}
                </div>
              )}
            </div>
            <Checkbox
              indeterminate={isSomeChecked}
              checked={isAllChecked}
              onChange={(e) => handleSelectAll(e.target.checked)}
            >
              <span style={{ fontSize: 12, color: 'rgba(255,255,255,0.45)' }}>全选建议</span>
            </Checkbox>
          </div>

          <div style={{ flex: 1, marginBottom: 16 }}>
            {group.suggestions.map((item) => {
              const isChecked = selectedRowKeys.includes(item.id);
              const curVal = getCurrentValue(item.fieldName, media);
              const sugVal = getSuggestedValueDisplay(item.fieldName, item.suggestedValue);
              
              const pct = Math.round((item.confidence ?? 0) * 100);
              let confColor = '#ff4d4f';
              if (pct >= 80) confColor = '#10b981';
              else if (pct >= 50) confColor = '#3b82f6';

              let fieldColor = 'default';
              if (item.fieldName === 'title') fieldColor = 'purple';
              else if (item.fieldName === 'overview') fieldColor = 'blue';
              else if (item.fieldName === 'originalTitle') fieldColor = 'cyan';
              else if (item.fieldName === 'rating') fieldColor = 'gold';
              else if (item.fieldName === 'releaseDate') fieldColor = 'orange';
              else if (item.fieldName.startsWith('tag:')) fieldColor = 'magenta';

              return (
                <div
                  key={item.id}
                  style={{
                    background: 'rgba(255,255,255,0.02)',
                    border: '1px solid rgba(255, 255, 255, 0.05)',
                    borderRadius: '8px',
                    padding: '8px 12px',
                    marginBottom: 8,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                    transition: 'all 0.2s',
                  }}
                  className={`card-suggestion-row ${isChecked ? 'checked' : ''}`}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Space size="middle">
                      <Checkbox
                        checked={isChecked}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setSelectedRowKeys((prev) => [...prev, item.id]);
                          } else {
                            setSelectedRowKeys((prev) => prev.filter((k) => k !== item.id));
                          }
                        }}
                      />
                      <Tag color={fieldColor} style={{ borderRadius: '4px', fontSize: 11, fontWeight: 500 }}>
                        {fieldLabel(item.fieldName)}
                      </Tag>
                    </Space>
                    <span style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)' }}>
                      置信度: <span style={{ color: confColor, fontWeight: 600 }}>{pct}%</span> | 来源: {item.providerId}
                    </span>
                  </div>

                  <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
                    <div style={{
                      flex: 1,
                      background: 'rgba(239, 68, 68, 0.05)',
                      border: '1px solid rgba(239, 68, 68, 0.15)',
                      borderRadius: '4px',
                      padding: '4px 8px',
                      fontSize: 12,
                      color: '#f87171',
                      textDecoration: 'line-through',
                      minHeight: 26,
                      wordBreak: 'break-all',
                    }}>
                      {curVal}
                    </div>
                    <div style={{
                      flex: 1,
                      background: 'rgba(16, 185, 129, 0.05)',
                      border: '1px solid rgba(16, 185, 129, 0.15)',
                      borderRadius: '4px',
                      padding: '4px 8px',
                      fontSize: 12,
                      color: '#34d399',
                      fontWeight: 500,
                      minHeight: 26,
                      wordBreak: 'break-all',
                    }}>
                      {sugVal}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* CARD ACTION BUTTONS */}
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 10,
            borderTop: '1px solid rgba(255, 255, 255, 0.06)',
            paddingTop: 12,
          }}>
            <Button
              size="small"
              type="text"
              onClick={() => onCompare(group.mediaItemId, media)}
              style={{ color: 'rgba(255,255,255,0.65)' }}
            >
              对比与合并
            </Button>
            <Button
              size="small"
              danger
              onClick={() => onRejectAll(suggestionIds)}
              style={{ borderRadius: 6 }}
            >
              全部拒绝
            </Button>
            <Button
              size="small"
              type="primary"
              onClick={() => onApproveAll(suggestionIds)}
              style={{
                borderRadius: 6,
                background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                borderColor: '#10b981',
              }}
            >
              全部采纳
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
};

const IntelligenceReview: React.FC = () => {
  const access = useAccess();
  const [data, setData] = useState<AiSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [layoutMode, setLayoutMode] = useState<'table' | 'card'>('card');

  // Drawer States
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [currentMediaId, setCurrentMediaId] = useState<number | null>(null);
  const [currentMedia, setCurrentMedia] = useState<MediaItem | null>(null);
  const [mediaLoading, setMediaLoading] = useState(false);
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<number[]>([]);
  const [submitting, setSubmitting] = useState(false);

  // Auto Review Config States
  const [autoConfig, setAutoConfig] = useState<AiConfigPayload | null>(null);
  const [autoConfigLoading, setAutoConfigLoading] = useState(false);
  const [showAutoPanel, setShowAutoPanel] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);

  // Form states for auto config
  const [autoEnabled, setAutoEnabled] = useState(false);
  const [autoThreshold, setAutoThreshold] = useState(80);
  const [autoFields, setAutoFields] = useState<string[]>([]);

  const load = async () => {
    setLoading(true);
    try {
      const res = await listAiSuggestions();
      if (res.code === 200) {
        setData(res.data || []);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadAutoConfig = async () => {
    if (!access.canManageSystem) return;
    setAutoConfigLoading(true);
    try {
      const res = await getAiConfig();
      if (res.code === 200 && res.data) {
        setAutoConfig(res.data);
        setAutoEnabled(res.data.autoApproveEnabled || false);
        setAutoThreshold(Math.round((res.data.autoApproveConfidenceThreshold || 0.8) * 100));
        
        const fieldsStr = res.data.autoApproveFields || '';
        if (fieldsStr === '*') {
          setAutoFields(['*']);
        } else {
          setAutoFields(fieldsStr.split(',').map((f) => f.trim()).filter(Boolean));
        }
      }
    } catch {
      message.error('无法获取自动审核配置');
    } finally {
      setAutoConfigLoading(false);
    }
  };

  useEffect(() => {
    load();
    loadAutoConfig();
  }, []);

  const stats = useMemo(() => {
    const total = data.length;
    const uniqueMedia = new Set(data.map((item) => item.mediaItemId)).size;
    const totalConfidence = data.reduce((sum, item) => sum + (item.confidence ?? 0), 0);
    const avgConfidence = total > 0 ? totalConfidence / total : 0;
    return { total, uniqueMedia, avgConfidence };
  }, [data]);

  const suggestionsForMedia = useMemo(() => {
    if (currentMediaId === null) return [];
    return data.filter((item) => item.mediaItemId === currentMediaId);
  }, [data, currentMediaId]);

  useEffect(() => {
    if (drawerVisible && suggestionsForMedia.length > 0) {
      setSelectedSuggestionIds(suggestionsForMedia.map((s) => s.id));
    }
  }, [drawerVisible, suggestionsForMedia]);

  const runBatch = async (action: 'approve' | 'reject') => {
    const ids = selectedRowKeys.map((key) => Number(key));
    if (ids.length === 0) {
      message.warning('请先选择建议');
      return;
    }
    const res =
      action === 'approve'
        ? await batchApproveSuggestions(ids)
        : await batchRejectSuggestions(ids);
    if (res.code === 200) {
      message.success(
        action === 'approve'
          ? `已批准 ${res.data?.approved ?? 0} 条 AI 建议`
          : `已拒绝 ${res.data?.rejected ?? 0} 条 AI 建议`,
      );
      setSelectedRowKeys([]);
      load();
    }
  };

  const handleApplySelected = async () => {
    if (selectedSuggestionIds.length === 0) {
      message.warning('请至少选择一项建议进行处理');
      return;
    }
    setSubmitting(true);
    try {
      const res = await batchApproveSuggestions(selectedSuggestionIds);
      if (res.code === 200) {
        message.success(`成功采纳了 ${res.data?.approved ?? 0} 项 AI 建议！`);
        setDrawerVisible(false);
        load();
      }
    } catch (err) {
      message.error('操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRejectSelected = async () => {
    if (selectedSuggestionIds.length === 0) {
      message.warning('请至少选择一项建议进行处理');
      return;
    }
    setSubmitting(true);
    try {
      const res = await batchRejectSuggestions(selectedSuggestionIds);
      if (res.code === 200) {
        message.success(`已拒绝了 ${res.data?.rejected ?? 0} 项 AI 建议`);
        setDrawerVisible(false);
        load();
      }
    } catch (err) {
      message.error('操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveAutoConfig = async () => {
    if (!autoConfig) return;
    setConfigSaving(true);
    try {
      const fieldsStr = autoFields.includes('*') ? '*' : autoFields.join(',');
      const payload: AiConfigPayload = {
        ...autoConfig,
        autoApproveEnabled: autoEnabled,
        autoApproveConfidenceThreshold: autoThreshold / 100,
        autoApproveFields: fieldsStr,
      };
      const res = await updateAiConfig(payload);
      if (res.code === 200) {
        message.success('自动审核规则更新成功！');
        setAutoConfig(res.data);
        setShowAutoPanel(false);
      }
    } catch {
      message.error('更新配置失败');
    } finally {
      setConfigSaving(false);
    }
  };

  const posterUrl = currentMedia
    ? resolveItemPosterUrl({
        itemId: currentMedia.id,
        posterPath: currentMedia.posterPath,
        type: currentMedia.type,
        fileIds: currentMedia.fileIds,
      })
    : null;

  const groupedData = useMemo(() => {
    const groups: Record<number, {
      mediaItemId: number;
      mediaTitle: string;
      suggestions: AiSuggestion[];
    }> = {};

    data.forEach((s) => {
      if (!groups[s.mediaItemId]) {
        groups[s.mediaItemId] = {
          mediaItemId: s.mediaItemId,
          mediaTitle: s.mediaTitle || `媒体 #${s.mediaItemId}`,
          suggestions: [],
        };
      }
      groups[s.mediaItemId].suggestions.push(s);
    });

    return Object.values(groups);
  }, [data]);

  const toggleFieldTag = (field: string) => {
    if (field === '*') {
      setAutoFields((prev) => (prev.includes('*') ? [] : ['*']));
      return;
    }
    setAutoFields((prev) => {
      let next = prev.filter((item) => item !== '*');
      if (next.includes(field)) {
        next = next.filter((item) => item !== field);
      } else {
        next.push(field);
      }
      return next;
    });
  };

  const handleCardApproveAll = async (ids: number[]) => {
    const res = await batchApproveSuggestions(ids);
    if (res.code === 200) {
      message.success(`成功采纳了该媒体的 ${res.data?.approved ?? 0} 项 AI 建议！`);
      load();
    }
  };

  const handleCardRejectAll = async (ids: number[]) => {
    const res = await batchRejectSuggestions(ids);
    if (res.code === 200) {
      message.success(`已拒绝该媒体的 ${res.data?.rejected ?? 0} 项 AI 建议`);
      load();
    }
  };

  const handleCardCompare = (mediaId: number, media: MediaItem | null) => {
    setCurrentMediaId(mediaId);
    setDrawerVisible(true);
    if (media) {
      setCurrentMedia(media);
    } else {
      setMediaLoading(true);
      setCurrentMedia(null);
      getItemDetail(mediaId)
        .then((res) => {
          if (res.code === 200) setCurrentMedia(res.data);
        })
        .catch(() => message.error('无法获取媒体详情'))
        .finally(() => setMediaLoading(false));
    }
  };

  const columns = [
    {
      title: '媒体',
      dataIndex: 'mediaTitle',
      width: 220,
      ellipsis: true,
      render: (title: string, row: AiSuggestion) => (
        <Link to={`/media/${row.mediaItemId}`} style={{ fontWeight: 500 }}>
          {title || `#${row.mediaItemId}`}
        </Link>
      ),
    },
    {
      title: '字段',
      dataIndex: 'fieldName',
      width: 150,
      render: (name: string) => {
        let color = 'default';
        if (name === 'title') color = 'purple';
        else if (name === 'overview') color = 'blue';
        else if (name === 'originalTitle') color = 'cyan';
        else if (name === 'rating') color = 'gold';
        else if (name === 'releaseDate') color = 'orange';
        else if (name.startsWith('tag:')) color = 'magenta';
        return (
          <Tag color={color} style={{ borderRadius: '6px', fontWeight: 500, padding: '2px 8px' }}>
            {fieldLabel(name)}
          </Tag>
        );
      },
    },
    { title: '建议值', dataIndex: 'suggestedValue', ellipsis: true },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 90,
      render: (value: number) => {
        const pct = Math.round((value ?? 0) * 100);
        let color = '#ff4d4f';
        if (pct >= 80) color = '#10b981';
        else if (pct >= 50) color = '#3b82f6';
        return (
          <span style={{ color, fontWeight: 'bold' }}>
            {pct}%
          </span>
        );
      },
    },
    { title: '来源', dataIndex: 'providerId', width: 100 },
    {
      title: '操作',
      width: 220,
      render: (_: unknown, row: AiSuggestion) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => handleCardCompare(row.mediaItemId, null)}
          >
            对比审核
          </Button>
          <Button
            type="link"
            size="small"
            style={{ color: '#10b981' }}
            onClick={async () => {
              const res = await approveSuggestion(row.id);
              if (res.code === 200) {
                message.success('建议已批准采纳');
                load();
              }
            }}
          >
            批准
          </Button>
          <Button
            type="link"
            size="small"
            danger
            onClick={async () => {
              const res = await rejectSuggestion(row.id);
              if (res.code === 200) {
                message.success('建议已拒绝');
                load();
              }
            }}
          >
            拒绝
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      title={
        <span style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <RobotOutlined style={{ color: '#8b5cf6' }} /> AI 建议审核与配置
        </span>
      }
      subTitle="审核 AI 补全的元数据或打标建议。已开启智能的自动审核模块，允许高置信度推荐直写并重新导出 NFO 刮削文件。"
      extra={
        <Space size="middle">
          {access.canManageSystem && (
            <Button
              type={showAutoPanel ? 'primary' : 'default'}
              icon={<SlidersOutlined />}
              onClick={() => setShowAutoPanel(!showAutoPanel)}
              style={{ borderRadius: 8, display: 'flex', alignItems: 'center' }}
            >
              自动审核规则
            </Button>
          )}
          <Button type="link" onClick={() => history.push('/settings/ai')}>
            AI 平台设置
          </Button>
        </Space>
      }
    >
      <style>{`
        /* Custom animations & interactive rules */
        .premium-media-card {
          position: relative;
        }
        .premium-media-card::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0; bottom: 0;
          border-radius: 16px;
          padding: 1px;
          background: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0.02) 100%);
          -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
          -webkit-mask-composite: xor;
          mask-composite: exclude;
          pointer-events: none;
          z-index: 1;
        }
        .premium-media-card:hover {
          transform: translateY(-4px);
          box-shadow: 0 12px 40px 0 rgba(139, 92, 246, 0.15) !important;
          border-color: rgba(139, 92, 246, 0.25) !important;
        }
        .media-card-title-link:hover {
          color: #c084fc !important;
        }
        .card-suggestion-row {
          transition: all 0.25s ease;
        }
        .card-suggestion-row.checked {
          background: rgba(139, 92, 246, 0.05) !important;
          border-color: rgba(139, 92, 246, 0.2) !important;
        }
        .glow-circle-icon {
          position: relative;
        }
        .glow-circle-icon::after {
          content: '';
          position: absolute;
          width: 8px; height: 8px;
          background: #a855f7;
          border-radius: 50%;
          top: 0; right: -2px;
          box-shadow: 0 0 10px #a855f7;
          animation: pulseGlow 2s infinite;
        }
        @keyframes pulseGlow {
          0% { transform: scale(0.9); opacity: 0.5; }
          50% { transform: scale(1.2); opacity: 1; box-shadow: 0 0 15px #a855f7; }
          100% { transform: scale(0.9); opacity: 0.5; }
        }
        .floating-action-bar {
          position: fixed;
          bottom: 24px;
          left: 50%;
          transform: translateX(-50%);
          z-index: 999;
          background: rgba(20, 20, 35, 0.7);
          backdrop-filter: blur(20px);
          border: 1px solid rgba(139, 92, 246, 0.35);
          box-shadow: 0 12px 40px rgba(0,0,0,0.5), 0 0 15px rgba(139, 92, 246, 0.15);
          border-radius: 40px;
          padding: 12px 32px;
          animation: slideUp 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
        }
        @keyframes slideUp {
          from { bottom: -100px; opacity: 0; }
          to { bottom: 24px; opacity: 1; }
        }
        .diff-drawer .ant-drawer-content {
          background: rgba(10, 10, 18, 0.93) !important;
          backdrop-filter: blur(30px) !important;
          border-left: 1px solid rgba(255, 255, 255, 0.08) !important;
        }
        .diff-drawer .ant-drawer-header {
          background: rgba(20, 20, 35, 0.6) !important;
          border-bottom: 1px solid rgba(255, 255, 255, 0.08) !important;
        }
        .diff-drawer .ant-drawer-title, .diff-drawer .ant-drawer-close {
          color: #fff !important;
        }
        .diff-item {
          margin-bottom: 16px;
          border-radius: 12px;
          border: 1px solid rgba(255, 255, 255, 0.06);
          background: rgba(255, 255, 255, 0.015);
          overflow: hidden;
          transition: all 0.25s ease;
        }
        .diff-item:hover {
          border-color: rgba(139, 92, 246, 0.2);
          background: rgba(255, 255, 255, 0.03);
        }
        .diff-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 10px 16px;
          background: rgba(255, 255, 255, 0.03);
          border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        }
        .diff-body {
          padding: 16px;
        }
        .diff-col {
          padding: 10px 14px;
          border-radius: 8px;
          font-size: 13px;
          line-height: 1.6;
          word-break: break-all;
          min-height: 44px;
          font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
        }
        .diff-removed {
          background: rgba(239, 68, 68, 0.06) !important;
          border: 1px solid rgba(239, 68, 68, 0.15) !important;
          color: #f87171 !important;
          text-decoration: line-through;
        }
        .diff-added {
          background: rgba(16, 185, 129, 0.06) !important;
          border: 1px solid rgba(16, 185, 129, 0.15) !important;
          color: #34d399 !important;
          font-weight: 500;
        }
        .diff-label-col {
          font-size: 12px;
          font-weight: 600;
          color: rgba(255,255,255,0.45);
        }
        .field-clickable-tag {
          cursor: pointer;
          user-select: none;
          transition: all 0.2s;
          border: 1px solid rgba(255, 255, 255, 0.08);
          background: rgba(255,255,255,0.02);
          color: rgba(255,255,255,0.6);
          border-radius: 6px;
          padding: 4px 12px;
        }
        .field-clickable-tag:hover {
          border-color: rgba(139, 92, 246, 0.5);
          color: #fff;
        }
        .field-clickable-tag.active {
          background: rgba(139, 92, 246, 0.15) !important;
          border-color: #8b5cf6 !important;
          color: #c084fc !important;
          font-weight: 500;
        }
        @media (max-width: 640px) {
          .floating-action-bar {
            right: 12px;
            bottom: 12px;
            left: 12px;
            transform: none;
            border-radius: 14px;
            padding: 12px;
          }
          .floating-action-bar .ant-space {
            width: 100%;
            justify-content: stretch;
          }
          .floating-action-bar .ant-btn {
            flex: 1 1 130px;
          }
          .diff-drawer .ant-drawer-body {
            padding: 16px !important;
          }
          .diff-header {
            align-items: flex-start;
            flex-direction: column;
            gap: 8px;
          }
          .diff-body {
            padding: 12px;
          }
          .diff-col {
            font-size: 12px;
          }
        }
      `}</style>

      {/* 1. AUTO REVIEW CONFIGURATION PANEL */}
      {showAutoPanel && autoConfig && (
        <Card
          style={{
            ...cardStyle,
            marginBottom: 20,
            border: '1px solid rgba(139, 92, 246, 0.25)',
            boxShadow: '0 8px 32px rgba(139, 92, 246, 0.08)',
          }}
          title={
            <span style={{ color: '#fff', fontSize: 15, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 8 }}>
              <ThunderboltOutlined style={{ color: '#c084fc' }} /> 自动审核与采纳规则配置
            </span>
          }
          extra={
            <Space>
              <Button size="small" onClick={() => setShowAutoPanel(false)} style={{ borderRadius: 6 }}>
                取消
              </Button>
              <Button
                size="small"
                type="primary"
                loading={configSaving}
                onClick={handleSaveAutoConfig}
                style={{
                  borderRadius: 6,
                  background: 'linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%)',
                  borderColor: '#8b5cf6',
                }}
              >
                保存配置
              </Button>
            </Space>
          }
        >
          <Spin spinning={autoConfigLoading}>
            <Row gutter={[24, 24]}>
              <Col xs={24} md={6} style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                <div style={{ marginBottom: 8, fontWeight: 500, color: '#fff', fontSize: 13 }}>
                  开启自动审核采纳
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <Switch
                    checked={autoEnabled}
                    onChange={setAutoEnabled}
                    checkedChildren="已启用"
                    unCheckedChildren="已关闭"
                  />
                  <span style={{ color: 'rgba(255,255,255,0.45)', fontSize: 12 }}>
                    开启后，符合要求的 AI 建议直写，不进待审表
                  </span>
                </div>
              </Col>
              
              <Col xs={24} md={8}>
                <div style={{ marginBottom: 4, fontWeight: 500, color: '#fff', fontSize: 13, display: 'flex', justifyContent: 'space-between' }}>
                  <span>置信度阈值限制</span>
                  <span style={{ color: '#c084fc', fontWeight: 600 }}>{autoThreshold}%</span>
                </div>
                <div style={{ padding: '0 8px' }}>
                  <Slider
                    min={50}
                    max={100}
                    value={autoThreshold}
                    onChange={setAutoThreshold}
                    tipFormatter={(val) => `${val}%`}
                  />
                </div>
                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginTop: 4 }}>
                  仅当 AI 输出建议的置信度大于等于此值时，方可触发自动采纳逻辑。
                </div>
              </Col>

              <Col xs={24} md={10}>
                <div style={{ marginBottom: 8, fontWeight: 500, color: '#fff', fontSize: 13 }}>
                  允许自动采纳的字段范围
                </div>
                <Space wrap size={[8, 8]}>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('*') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('*')}
                  >
                    全部字段
                  </Tag>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('tag:*') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('tag:*')}
                  >
                    自动打标 (tag:*)
                  </Tag>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('overview') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('overview')}
                  >
                    简介 (overview)
                  </Tag>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('rating') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('rating')}
                  >
                    评分 (rating)
                  </Tag>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('releaseDate') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('releaseDate')}
                  >
                    发行日期 (releaseDate)
                  </Tag>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('title') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('title')}
                  >
                    标题 (title)
                  </Tag>
                  <Tag
                    className={`field-clickable-tag ${autoFields.includes('originalTitle') ? 'active' : ''}`}
                    onClick={() => toggleFieldTag('originalTitle')}
                  >
                    原标题 (originalTitle)
                  </Tag>
                </Space>
                <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginTop: 8 }}>
                  勾选字段将自动应用审核。未选中的字段仍将照常写入 PENDING 待审表。
                </div>
              </Col>
            </Row>
          </Spin>
        </Card>
      )}

      {/* 2. STATS OVERVIEW DASHBOARD */}
      <Row gutter={[16, 16]} style={{ marginBottom: 20 }}>
        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle} bodyStyle={{ padding: '18px 24px' }} hoverable>
            <Statistic
              title={<span style={{ color: 'rgba(255,255,255,0.45)', fontSize: 12 }}>待审核建议数</span>}
              value={stats.total}
              valueStyle={{ color: '#c084fc', fontWeight: 700, fontSize: 28 }}
              prefix={<BulbOutlined style={{ marginRight: 8, color: '#c084fc' }} />}
            />
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginTop: 8 }}>
              等待人工介入或对比采纳
            </div>
          </Card>
        </Col>
        
        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle} bodyStyle={{ padding: '18px 24px' }} hoverable>
            <Statistic
              title={<span style={{ color: 'rgba(255,255,255,0.45)', fontSize: 12 }}>受影响媒体数</span>}
              value={stats.uniqueMedia}
              valueStyle={{ color: '#60a5fa', fontWeight: 700, fontSize: 28 }}
              prefix={<VideoCameraOutlined style={{ marginRight: 8, color: '#60a5fa' }} />}
            />
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginTop: 8 }}>
              当前待优化的电影或电视列表
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle} bodyStyle={{ padding: '18px 24px' }} hoverable>
            <Statistic
              title={<span style={{ color: 'rgba(255,255,255,0.45)', fontSize: 12 }}>模型平均置信度</span>}
              value={Math.round(stats.avgConfidence * 100)}
              suffix="%"
              valueStyle={{ color: '#10b981', fontWeight: 700, fontSize: 28 }}
              prefix={<PercentageOutlined style={{ marginRight: 8, color: '#10b981' }} />}
            />
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginTop: 8 }}>
              模型对输出的综合精确度指标
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card
            style={{
              ...cardStyle,
              border: autoEnabled ? '1px solid rgba(16, 185, 129, 0.2)' : '1px solid rgba(255,255,255,0.08)'
            }}
            bodyStyle={{ padding: '18px 24px' }}
            hoverable
          >
            <Statistic
              title={<span style={{ color: 'rgba(255,255,255,0.45)', fontSize: 12 }}>AI 自动采纳状态</span>}
              value={autoEnabled ? '已开启' : '未启用'}
              valueStyle={{ color: autoEnabled ? '#10b981' : 'rgba(255,255,255,0.4)', fontWeight: 700, fontSize: 28 }}
              prefix={
                <span className={autoEnabled ? 'glow-circle-icon' : ''}>
                  <RobotOutlined style={{ marginRight: 8, color: autoEnabled ? '#10b981' : 'rgba(255,255,255,0.3)' }} />
                </span>
              }
            />
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.35)', marginTop: 8, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
              {autoEnabled ? `阈值: ${autoThreshold}% | 字段: ${autoFields.includes('*') ? '全部' : autoFields.length}` : '未启用后台策略直写'}
            </div>
          </Card>
        </Col>
      </Row>

      {/* 3. LAYOUT CONTROLLER & CONTROLS */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ color: '#fff', fontSize: 14, fontWeight: 500 }}>
          待审核条目表 ({data.length})
        </div>
        <Space size="middle">
          <Segmented
            value={layoutMode}
            onChange={(val) => setLayoutMode(val as 'table' | 'card')}
            options={[
              { label: '表格视图', value: 'table', icon: <TableOutlined /> },
              { label: '卡片差异视图', value: 'card', icon: <AppstoreOutlined /> },
            ]}
          />
        </Space>
      </div>

      {/* 4. MAIN DATA VIEW LIST */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Spin size="large" tip="载入 AI 建议中..." />
        </div>
      ) : data.length === 0 ? (
        <Card style={tableCardStyle} bodyStyle={{ padding: '80px 0' }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <div style={{ color: 'rgba(255,255,255,0.35)', fontSize: 14 }}>
                暂无待审核的 AI 建议，刮削或打标完成后将自动呈现于此。
              </div>
            }
          />
        </Card>
      ) : layoutMode === 'table' ? (
        <Card style={tableCardStyle} bodyStyle={{ padding: 0 }}>
          <Table<AiSuggestion>
            rowKey="id"
            dataSource={data}
            columns={columns}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
            }}
            pagination={{ pageSize: 20, showSizeChanger: true }}
            style={{ background: 'transparent' }}
          />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          {groupedData.map((group) => (
            <Col xs={24} key={group.mediaItemId}>
              <MediaCardItem
                group={group}
                onApproveAll={handleCardApproveAll}
                onRejectAll={handleCardRejectAll}
                onCompare={handleCardCompare}
                selectedRowKeys={selectedRowKeys}
                setSelectedRowKeys={setSelectedRowKeys}
              />
            </Col>
          ))}
        </Row>
      )}

      {/* 5. FLOATING BOTTOM BATCH ACTIONS TOOLBAR */}
      {selectedRowKeys.length > 0 && (
        <div className="floating-action-bar">
          <Space size="large">
            <span style={{ color: '#fff', fontSize: 13 }}>
              已选中 <span style={{ color: '#c084fc', fontWeight: 'bold', fontSize: 15 }}>{selectedRowKeys.length}</span> 条 AI 审核建议
            </span>
            <Space size="middle">
              <Button
                type="text"
                onClick={() => setSelectedRowKeys([])}
                style={{ color: 'rgba(255,255,255,0.45)', borderRadius: 20 }}
              >
                取消选择
              </Button>
              <Button
                danger
                onClick={() => runBatch('reject')}
                style={{ borderRadius: 20, padding: '4px 20px', fontWeight: 500 }}
              >
                批量拒绝
              </Button>
              <Button
                type="primary"
                onClick={() => runBatch('approve')}
                style={{
                  borderRadius: 20,
                  padding: '4px 24px',
                  fontWeight: 600,
                  background: 'linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%)',
                  borderColor: '#8b5cf6',
                  boxShadow: '0 4px 15px rgba(139, 92, 246, 0.4)',
                }}
              >
                批量批准采纳
              </Button>
            </Space>
          </Space>
        </div>
      )}

      {/* 6. COMPARE & MERGE SIDE DRAWER */}
      <Drawer
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ThunderboltOutlined style={{ color: '#c084fc' }} /> 对比审核与合并采纳
          </span>
        }
        placement="right"
        width={750}
        onClose={() => setDrawerVisible(false)}
        visible={drawerVisible}
        className="diff-drawer"
        bodyStyle={{ padding: 24 }}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
            <Button onClick={() => setDrawerVisible(false)} disabled={submitting}>取消</Button>
            <Button
              danger
              onClick={handleRejectSelected}
              loading={submitting}
              disabled={selectedSuggestionIds.length === 0}
            >
              拒绝选中建议
            </Button>
            <Button
              type="primary"
              onClick={handleApplySelected}
              loading={submitting}
              disabled={selectedSuggestionIds.length === 0}
              style={{
                background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                borderColor: '#10b981',
              }}
            >
              采纳选中修改
            </Button>
          </div>
        }
      >
        {mediaLoading ? (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <Spin size="large" tip="获取媒体详情中..." />
          </div>
        ) : currentMedia ? (
          <div>
            <div style={{
              display: 'flex',
              gap: 20,
              background: 'rgba(255,255,255,0.02)',
              padding: 20,
              borderRadius: 16,
              border: '1px solid rgba(255,255,255,0.06)',
              marginBottom: 24,
              boxShadow: '0 8px 32px rgba(0,0,0,0.15)',
              position: 'relative',
              overflow: 'hidden',
            }}>
              {posterUrl ? (
                <img
                  src={posterUrl}
                  alt="poster"
                  style={{
                    width: 80,
                    height: 114,
                    borderRadius: 8,
                    objectFit: 'cover',
                    boxShadow: '0 4px 16px rgba(0,0,0,0.4)',
                    border: '1px solid rgba(255,255,255,0.1)'
                  }}
                />
              ) : (
                <div style={{
                  width: 80,
                  height: 114,
                  borderRadius: 8,
                  background: 'rgba(255,255,255,0.02)',
                  border: '1px dashed rgba(255,255,255,0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'rgba(255,255,255,0.2)',
                  fontSize: 12
                }}>无封面</div>
              )}
              <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                <h3 style={{ margin: '0 0 8px', fontSize: 18, color: '#fff', fontWeight: 600 }}>{currentMedia.title}</h3>
                <Space size="middle" style={{ marginTop: 4 }}>
                  <Tag color="purple" style={{ border: 'none', borderRadius: 4 }}>{currentMedia.type}</Tag>
                  <span style={{ color: 'rgba(255,255,255,0.4)', fontSize: 12 }}>
                    发行年份: {currentMedia.releaseDate || '未知'}
                  </span>
                  <span style={{ color: 'rgba(255,255,255,0.4)', fontSize: 12 }}>
                    评分: {currentMedia.rating !== undefined ? currentMedia.rating : '无'}
                  </span>
                </Space>
              </div>
            </div>

            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 16
            }}>
              <span style={{ fontSize: 14, fontWeight: 600, color: '#fff', display: 'flex', alignItems: 'center', gap: 6 }}>
                <InfoCircleOutlined style={{ color: '#8b5cf6' }} /> 待审字段差异对比
              </span>
              <Checkbox
                indeterminate={
                  selectedSuggestionIds.length > 0 &&
                  selectedSuggestionIds.length < suggestionsForMedia.length
                }
                checked={selectedSuggestionIds.length === suggestionsForMedia.length}
                onChange={(e) => {
                  if (e.target.checked) {
                    setSelectedSuggestionIds(suggestionsForMedia.map((s) => s.id));
                  } else {
                    setSelectedSuggestionIds([]);
                  }
                }}
              >
                <span style={{ fontSize: 12, color: 'rgba(255,255,255,0.45)' }}>全选修改</span>
              </Checkbox>
            </div>

            <List
              dataSource={suggestionsForMedia}
              renderItem={(item) => {
                const isChecked = selectedSuggestionIds.includes(item.id);
                const currentVal = getCurrentValue(item.fieldName, currentMedia);
                const suggestedVal = getSuggestedValueDisplay(item.fieldName, item.suggestedValue);

                return (
                  <div className="diff-item" key={item.id}>
                    <div className="diff-header">
                      <Space>
                        <Checkbox
                          checked={isChecked}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedSuggestionIds([...selectedSuggestionIds, item.id]);
                            } else {
                              setSelectedSuggestionIds(
                                selectedSuggestionIds.filter((id) => id !== item.id)
                              );
                            }
                          }}
                        />
                        <span style={{ fontWeight: 600, color: '#fff', fontSize: 13 }}>
                          {fieldLabel(item.fieldName)}
                        </span>
                      </Space>
                      <Tag color="cyan" style={{ border: 'none', borderRadius: '4px' }}>
                        置信度: {Math.round((item.confidence ?? 0) * 100)}%
                      </Tag>
                    </div>
                    <div className="diff-body">
                      <Row gutter={[16, 12]}>
                        <Col span={12}>
                          <div className="diff-label-col" style={{ marginBottom: 6 }}>当前数据库值</div>
                          <div className="diff-col diff-removed">{currentVal}</div>
                        </Col>
                        <Col span={12}>
                          <div className="diff-label-col" style={{ marginBottom: 6 }}>AI 智能建议值</div>
                          <div className="diff-col diff-added">{suggestedVal}</div>
                        </Col>
                      </Row>
                    </div>
                  </div>
                );
              }}
            />
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '60px 0', color: 'rgba(255,255,255,0.35)' }}>
            暂无媒体详情
          </div>
        )}
      </Drawer>
    </PageContainer>
  );
};

export default IntelligenceReview;
