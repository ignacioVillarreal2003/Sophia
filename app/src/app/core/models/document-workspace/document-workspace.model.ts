import { DocumentWorkspaceRole, DocumentWorkspaceStatus, MemberStatus } from './enums';

export interface WorkspaceUsage {
  readonly usedBytes: number;
  readonly quotaMb: number | null;
}

export interface DocumentWorkspace {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly logoUrl: string | null;
  readonly color: string | null;
  readonly language: string | null;
  readonly ownerId: string;
  readonly status: DocumentWorkspaceStatus;
  /** Days the trash keeps items; null means the system default. */
  readonly trashRetentionDays: number | null;
  readonly publicSharingEnabled: boolean;
  readonly timezone: string;
  readonly dateFormat: string;
  readonly myRole: DocumentWorkspaceRole;
  readonly createdAt: string;
  readonly updatedAt: string;
  /** User who last changed the workspace. */
  readonly updatedBy: string | null;
  /** Only set for soft-deleted workspaces (trash listing). */
  readonly deletedAt?: string | null;
}

export interface DocumentWorkspaceListQuery {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
  role?: DocumentWorkspaceRole;
  status?: DocumentWorkspaceStatus;
  [key: string]: string | number | boolean | undefined;
}

export interface CreateDocumentWorkspaceRequest {
  name: string;
  description?: string;
  color?: string;
  language?: string;
}

export interface UpdateDocumentWorkspaceRequest {
  name?: string;
  description?: string;
  color?: string;
  language?: string;
}

export interface DocumentWorkspaceMember {
  readonly id: string;
  readonly userId: string;
  readonly fullName: string;
  readonly email: string;
  readonly photoUrl: string | null;
  readonly role: DocumentWorkspaceRole;
  readonly status: MemberStatus;
  readonly invitedBy: string | null;
  readonly updatedBy: string | null;
  readonly joinedAt: string;
}

export interface AddMemberRequest {
  email: string;
  role: DocumentWorkspaceRole;
}
