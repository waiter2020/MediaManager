import React from 'react';
import { Select } from 'antd';

export interface TagOption {
  id: number;
  name: string;
}

interface Props {
  allTags: TagOption[];
  assignedTagIds?: number[];
  loading?: boolean;
  value?: number;
  onChange?: (tagId: number | undefined) => void;
  onSelect?: (tagId: number) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}

const TagSelect: React.FC<Props> = ({
  allTags,
  assignedTagIds = [],
  loading,
  value,
  onChange,
  onSelect,
  placeholder = '添加标签',
  style,
}) => {
  const assigned = new Set(assignedTagIds);
  const options = allTags
    .filter((tag) => !assigned.has(tag.id))
    .map((tag) => ({ label: tag.name, value: tag.id }));

  return (
    <Select
      showSearch
      allowClear
      placeholder={placeholder}
      optionFilterProp="label"
      loading={loading}
      value={value}
      style={style ?? { width: 200 }}
      options={options}
      onChange={(tagId) => {
        onChange?.(tagId);
        if (tagId != null) onSelect?.(tagId);
      }}
    />
  );
};

export default TagSelect;
