import React, { useState } from 'react';
import type { CSSProperties } from 'react';
import { SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Input } from 'antd';
import './index.css';

interface UnifiedSearchBoxProps {
  value?: string;
  defaultValue?: string;
  placeholder?: string;
  libraryId?: number | null;
  className?: string;
  style?: CSSProperties;
  size?: 'large' | 'middle' | 'small';
  loading?: boolean;
  autoFocus?: boolean;
  onChange?: (value: string) => void;
  onSearch?: (value: string) => void;
  onClear?: () => void;
}

const DEFAULT_PLACEHOLDER = '搜索标题、标签、剧情，或输入自然语言';

const UnifiedSearchBox: React.FC<UnifiedSearchBoxProps> = ({
  value,
  defaultValue = '',
  placeholder = DEFAULT_PLACEHOLDER,
  libraryId,
  className,
  style,
  size = 'large',
  loading,
  autoFocus,
  onChange,
  onSearch,
  onClear,
}) => {
  const [innerValue, setInnerValue] = useState(defaultValue);
  const controlled = value !== undefined;
  const currentValue = controlled ? value : innerValue;

  const setValue = (nextValue: string) => {
    if (!controlled) {
      setInnerValue(nextValue);
    }
    onChange?.(nextValue);
  };

  const submit = (rawValue?: string) => {
    const nextQuery = (rawValue ?? currentValue).trim();
    if (!nextQuery) {
      onClear?.();
      return;
    }

    if (onSearch) {
      onSearch(nextQuery);
      return;
    }

    const params = new URLSearchParams();
    params.set('q', nextQuery);
    if (libraryId != null) {
      params.set('libraryId', String(libraryId));
    }
    history.push(`/browse?${params.toString()}`);
  };

  return (
    <div className={['unified-search-box', className].filter(Boolean).join(' ')} style={style}>
      <Input.Search
        className="unified-search-input"
        value={currentValue}
        onChange={(event) => {
          const nextValue = event.target.value;
          setValue(nextValue);
          if (!nextValue) {
            onClear?.();
          }
        }}
        onSearch={submit}
        placeholder={placeholder}
        prefix={<SearchOutlined className="unified-search-icon" />}
        enterButton="搜索"
        allowClear
        size={size}
        loading={loading}
        autoFocus={autoFocus}
      />
    </div>
  );
};

export default UnifiedSearchBox;
