export interface ApiResponse<T = any> {
  code: number;
  message?: string;
  data: T;
  timestamp?: string;
}

export interface PageResult<T = any> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

