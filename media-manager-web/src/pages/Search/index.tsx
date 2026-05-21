import React, { useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-components';
import { Input, Segmented, Card, message, Empty, Select } from 'antd';
import { history } from '@umijs/max';
import { searchKeyword, searchSemantic, searchQuery } from '@/services/search';
import { getLibraries } from '@/services/library';
import MediaCard from '@/components/MediaCard';

const SearchPage: React.FC = () => {
  const [mode, setMode] = useState<string>('keyword');
  const [query, setQuery] = useState('');
  const [libraryId, setLibraryId] = useState<number | undefined>();
  const [libraries, setLibraries] = useState<{ id: number; name: string }[]>([]);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<any[]>([]);
  const [searched, setSearched] = useState(false);

  useEffect(() => {
    getLibraries().then((res) => {
      if (res.code === 200) {
        setLibraries((res.data || []).map((l: any) => ({ id: l.id, name: l.name })));
      }
    });
  }, []);

  const runSearch = async () => {
    if (!query.trim()) {
      return;
    }
    setLoading(true);
    setSearched(true);
    try {
      if (mode === 'semantic') {
        const res = await searchSemantic({ query, libraryId, limit: 30 });
        if (res.code === 200) {
          setResults(res.data || []);
        }
      } else if (mode === 'nl') {
        const res = await searchQuery({ query, libraryId, page: 1, size: 30 });
        if (res.code === 200) {
          setResults(res.data?.items || []);
        }
      } else {
        const res = await searchKeyword(query, { libraryId, page: 1, size: 30 });
        if (res.code === 200) {
          setResults(res.data?.items || []);
        }
      }
    } catch {
      message.error('搜索失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageContainer title="搜索">
      <Card style={{ marginBottom: 16 }}>
        <Segmented
          options={[
            { label: '关键词', value: 'keyword' },
            { label: '语义', value: 'semantic' },
            { label: '自然语言', value: 'nl' },
          ]}
          value={mode}
          onChange={setMode}
          style={{ marginBottom: 12 }}
        />
        <Select
          allowClear
          placeholder="全部媒体库"
          style={{ width: 220, marginBottom: 12 }}
          value={libraryId}
          onChange={setLibraryId}
          options={libraries.map((l) => ({ value: l.id, label: l.name }))}
        />
        <Input.Search
          placeholder={mode === 'nl' ? '例如：2020年后的科幻电影' : '输入搜索词'}
          enterButton="搜索"
          size="large"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onSearch={runSearch}
          loading={loading}
        />
      </Card>
      {loading && results.length === 0 ? (
        <div className="media-grid">
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={i} className="skeleton-card">
              <div className="skeleton-poster" />
            </div>
          ))}
        </div>
      ) : searched && !loading && results.length === 0 ? (
        <Empty description="未找到匹配的媒体" />
      ) : (
        <div className="media-grid">
          {results.map((item: any) => (
            <MediaCard
              key={item.id}
              id={item.id}
              title={item.title}
              type={item.type}
              posterPath={item.posterPath}
              fileIds={item.fileIds}
              rating={item.rating}
              releaseDate={item.releaseDate}
              overview={item.overview}
              libraryName={item.libraryName}
              onClick={() => history.push(`/media/${item.id}`)}
            />
          ))}
        </div>
      )}
    </PageContainer>
  );
};

export default SearchPage;
