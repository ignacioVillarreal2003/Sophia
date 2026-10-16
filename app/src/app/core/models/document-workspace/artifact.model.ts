import { ArtifactType } from './enums';

export interface Artifact {
  readonly id: string;
  readonly workspaceId: string;
  readonly parentId: string | null;
  readonly name: string;
  readonly type: ArtifactType;
  readonly ownerId: string;
  readonly description?: string | null;
  /** Accent color of a folder. */
  readonly color?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly deletedAt: string | null;

  readonly contentVersion?: number | null;

  readonly mimeType?: string | null;
  readonly sizeBytes?: number | null;
  readonly originalFilename?: string | null;
}

export interface CreateArtifactRequest {
  parentId?: string | null;
  name: string;
  type: ArtifactType;
  description?: string;
  color?: string;
  /** Initial Markdown (or workbook JSON for spreadsheets). */
  content?: string;
}

export interface UpdateArtifactRequest {
  name?: string;
  /** An empty value clears it. */
  description?: string;
  color?: string;
}

/** `newParentId: null` moves the item to the workspace root. */
export interface MoveArtifactRequest {
  newParentId: string | null;
}

export interface BreadcrumbEntry {
  readonly id: string;
  readonly name: string;
  readonly type: ArtifactType;
}

export interface Favorite {
  readonly id: string;
  readonly workspaceId: string;
  readonly artifactId: string;
  readonly pinned: boolean;
  readonly createdAt: string;
  readonly artifact: Artifact;
}

export interface ArtifactNode extends Artifact {
  children?: ArtifactNode[];
  expanded?: boolean;
  loading?: boolean;
}
