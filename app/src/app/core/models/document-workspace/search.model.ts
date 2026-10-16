import { ArtifactType } from './enums';

export interface SearchHit {
  readonly id: string;
  readonly workspaceId: string;
  readonly name: string;
  readonly type: ArtifactType;
  readonly snippet: string | null;
  readonly parentId: string | null;
  readonly score: number;
  readonly updatedAt: string;
}

export interface SearchResult {
  readonly hits: SearchHit[];
  readonly total: number;
  readonly took: number;
}

export interface Template {
  readonly id: string;
  readonly workspaceId: string;
  readonly name: string;
  readonly description: string | null;
  readonly category: string | null;
  readonly published: boolean;
  readonly version: number;
  readonly createdAt: string;
}
