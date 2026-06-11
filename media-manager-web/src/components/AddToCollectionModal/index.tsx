import React, { useEffect, useState } from 'react';
import { Input, Modal, Select, message } from 'antd';
import {
  addItemsToCollection,
  createCollection,
  listCollections,
  type MediaCollection,
} from '@/services/collection';

export interface AddToCollectionModalProps {
  open: boolean;
  itemIds: number[];
  title?: string;
  onClose: () => void;
  onSuccess?: () => void;
}

const AddToCollectionModal: React.FC<AddToCollectionModalProps> = ({
  open,
  itemIds,
  title,
  onClose,
  onSuccess,
}) => {
  const [collections, setCollections] = useState<MediaCollection[]>([]);
  const [selectedCollectionId, setSelectedCollectionId] = useState<number | undefined>();
  const [newCollectionName, setNewCollectionName] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setSelectedCollectionId(undefined);
    setNewCollectionName('');
    listCollections().then((res) => {
      if (res.code === 200) {
        setCollections(res.data || []);
      }
    });
  }, [open]);

  const handleSubmit = async () => {
    if (itemIds.length === 0) {
      return;
    }
    setSaving(true);
    try {
      if (selectedCollectionId) {
        const res = await addItemsToCollection(selectedCollectionId, itemIds);
        if (res.code === 200) {
          message.success(itemIds.length > 1 ? `已将 ${itemIds.length} 项加入合集` : '已加入合集');
          onClose();
          onSuccess?.();
        }
        return;
      }
      const name = newCollectionName.trim();
      if (!name) {
        message.warning('请选择合集或输入新合集名称');
        return;
      }
      const res = await createCollection({
        name,
        type: 'COLLECTION',
        visibility: 'PRIVATE',
        itemIds,
      });
      if (res.code === 200) {
        message.success(
          itemIds.length > 1 ? `已创建合集并加入 ${itemIds.length} 项` : '已创建合集并加入当前媒体',
        );
        onClose();
        onSuccess?.();
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={title ?? (itemIds.length > 1 ? `加入合集：${itemIds.length} 项` : '加入合集')}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      okText="加入"
      destroyOnClose
    >
      <div style={{ display: 'grid', gap: 16, marginTop: 16 }}>
        <Select
          allowClear
          placeholder="选择已有合集"
          value={selectedCollectionId}
          onChange={setSelectedCollectionId}
          options={collections
            .filter((collection) => !collection.smart)
            .map((collection) => ({
              label: `${collection.name} (${collection.itemCount || 0})`,
              value: collection.id,
            }))}
        />
        <Input
          placeholder="或输入新合集名称"
          value={newCollectionName}
          onChange={(event) => setNewCollectionName(event.target.value)}
          disabled={selectedCollectionId != null}
          maxLength={128}
        />
      </div>
    </Modal>
  );
};

export default AddToCollectionModal;
