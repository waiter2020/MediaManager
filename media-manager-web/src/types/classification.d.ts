export interface Tag {
  id: number;
  name: string;
  color?: string;
  source?: 'MANUAL' | 'AUTO' | 'RULE';
}

export interface Category {
  id: number;
  name: string;
  parentId?: number | null;
  type?: string;
  children?: Category[];
}

export interface ClassificationRule {
  id: number;
  name: string;
  enabled: boolean;
  expression: string;
  target: string;
  createdAt?: string;
}

