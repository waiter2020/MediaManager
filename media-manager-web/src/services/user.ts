import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import type { User } from '@/types/user';

export interface UserCreatePayload {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
}

export interface UserUpdatePayload {
  displayName?: string;
  email?: string;
  enabled?: boolean;
}

export interface LibraryAccessItem {
  libraryId: number;
  canView?: boolean;
  canEdit?: boolean;
  canDeleteFile?: boolean;
}

export async function getUsers() {
  return request<ApiResponse<User[]>>('/api/v1/users', { method: 'GET' });
}

export async function getUser(id: number) {
  return request<ApiResponse<User>>(`/api/v1/users/${id}`, { method: 'GET' });
}

export async function createUser(data: UserCreatePayload) {
  return request<ApiResponse<User>>('/api/v1/users', { method: 'POST', data });
}

export async function updateUser(id: number, data: UserUpdatePayload) {
  return request<ApiResponse<User>>(`/api/v1/users/${id}`, { method: 'PUT', data });
}

export async function deleteUser(id: number) {
  return request<ApiResponse<void>>(`/api/v1/users/${id}`, { method: 'DELETE' });
}

export async function assignRoles(id: number, data: { roleCodes: string[] }) {
  return request<ApiResponse<void>>(`/api/v1/users/${id}/roles`, { method: 'PUT', data });
}

export async function getLibraryAccess(id: number) {
  return request<ApiResponse<LibraryAccessItem[]>>(`/api/v1/users/${id}/library-access`, {
    method: 'GET',
  });
}

export async function setLibraryAccess(id: number, data: { items: LibraryAccessItem[] }) {
  return request<ApiResponse<void>>(`/api/v1/users/${id}/library-access`, { method: 'PUT', data });
}

export async function getCurrentUser() {
  return request<ApiResponse<User>>('/api/v1/users/me', { method: 'GET' });
}

export async function updateCurrentUser(data: UserUpdatePayload) {
  return request<ApiResponse<User>>('/api/v1/users/me', { method: 'PUT', data });
}

export async function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request<ApiResponse<void>>('/api/v1/users/me/password', { method: 'PUT', data });
}
