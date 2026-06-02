import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import type { LibraryPluginConfig } from '@/types/library';

export interface PluginCatalogItem {
  id: string;
  pluginId?: string;
  kind: string;
  displayName?: string;
  name?: string;
  description?: string;
}

export async function listPlugins() {
  return request<ApiResponse<PluginCatalogItem[]>>('/api/v1/plugins', { method: 'GET' });
}

export async function listLibraryPlugins(libraryId: number) {
  return request<ApiResponse<LibraryPluginConfig[]>>(`/api/v1/libraries/${libraryId}/plugins`, {
    method: 'GET',
  });
}

export async function updateLibraryPlugins(libraryId: number, configs: LibraryPluginConfig[]) {
  return request<ApiResponse<LibraryPluginConfig[]>>(`/api/v1/libraries/${libraryId}/plugins`, {
    method: 'PUT',
    data: configs,
  });
}

export async function applyDefaultLibraryPlugins(libraryId: number) {
  return request<ApiResponse<LibraryPluginConfig[]>>(
    `/api/v1/libraries/${libraryId}/plugins/apply-default`,
    { method: 'POST' },
  );
}
