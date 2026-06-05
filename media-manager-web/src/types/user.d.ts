export interface Role {
  id: number;
  code: string;
  name: string;
  description?: string;
  builtIn?: boolean;
}

export interface Permission {
  id: number;
  code: string;
  name: string;
  groupName?: string;
}

export interface User {
  id: number;
  username: string;
  displayName?: string;
  email?: string;
  avatarPath?: string;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
  roles?: Role[];
}

