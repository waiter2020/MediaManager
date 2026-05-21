import { request } from '@umijs/max';

export async function getUsers() {
  return request('/api/v1/users', { method: 'GET' });
}

export async function getUser(id: number) {
  return request(`/api/v1/users/${id}`, { method: 'GET' });
}

export async function createUser(data: any) {
  return request('/api/v1/users', { method: 'POST', data });
}

export async function updateUser(id: number, data: any) {
  return request(`/api/v1/users/${id}`, { method: 'PUT', data });
}

export async function deleteUser(id: number) {
  return request(`/api/v1/users/${id}`, { method: 'DELETE' });
}

export async function assignRoles(id: number, data: { roleCodes: string[] }) {
  return request(`/api/v1/users/${id}/roles`, { method: 'PUT', data });
}

export async function getLibraryAccess(id: number) {
  return request(`/api/v1/users/${id}/library-access`, { method: 'GET' });
}

export async function setLibraryAccess(id: number, data: { items: LibraryAccessItem[] }) {
  return request(`/api/v1/users/${id}/library-access`, { method: 'PUT', data });
}

export interface LibraryAccessItem {
  libraryId: number;
  canView?: boolean;
  canEdit?: boolean;
  canDeleteFile?: boolean;
}

export async function getCurrentUser() {
  return request('/api/v1/users/me', { method: 'GET' });
}

export async function updateCurrentUser(data: any) {
  return request('/api/v1/users/me', { method: 'PUT', data });
}

export async function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request('/api/v1/users/me/password', { method: 'PUT', data });
}
