export interface ApiResponse<T = unknown> {
  code: number;
  message?: string;
  data: T;
  timestamp?: string;
}

export interface PageResult<T = unknown> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

declare namespace API {
  interface CurrentUser {
    id?: number;
    username?: string;
    displayName?: string;
    avatarPath?: string;
    permissions?: string[];
    roles?: { code: string; name?: string }[];
  }
}

