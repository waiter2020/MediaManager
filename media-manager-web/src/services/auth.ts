import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthUser {
  id?: number;
  username?: string;
  displayName?: string;
  avatarPath?: string;
  permissions?: string[];
  roles?: Array<string | { code: string; name?: string }>;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user?: AuthUser;
}

export interface SetupStatus {
  setupCompleted?: boolean;
}

export async function login(data: LoginRequest) {
  return request<ApiResponse<LoginResponse>>('/api/v1/auth/login', { method: 'POST', data });
}

export async function logout(refreshToken: string) {
  return request<ApiResponse<void>>('/api/v1/auth/logout', {
    method: 'POST',
    params: { refreshToken },
  });
}

export async function refresh(refreshToken: string) {
  return request<ApiResponse<LoginResponse>>('/api/v1/auth/refresh', {
    method: 'POST',
    data: { refreshToken },
  });
}

export async function setup(data: LoginRequest) {
  return request<ApiResponse<void>>('/api/v1/auth/setup', { method: 'POST', data });
}

export async function getSetupStatus() {
  return request<ApiResponse<SetupStatus>>('/api/v1/auth/setup/status', { method: 'GET' });
}
