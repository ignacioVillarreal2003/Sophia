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
  /** Last change anywhere in the workspace (content or people). */
  readonly lastActivityAt?: string | null;
  /** Trash listing only: when the workspace will be erased for good. */
  readonly purgeAt?: string | null;
  /** The caller pinned it to their favorites. */
  readonly pinned?: boolean;
  /** When the caller last opened it. */
  readonly lastAccessedAt?: string | null;
  /** Only present when the list is asked for stats (detailed cards). */
  readonly stats?: WorkspaceStats | null;
}

export interface WorkspaceMemberPreview {
  readonly userId: string;
  readonly displayName: string | null;
  readonly email: string;
}

export interface WorkspaceStats {
  readonly memberCount: number;
  readonly itemCount: number;
  readonly usedBytes: number;
  /** Quota in MB; null means unlimited. */
  readonly quotaMb: number | null;
  readonly members: WorkspaceMemberPreview[];
}

export type WorkspaceOwnership = 'MINE' | 'SHARED';
export type WorkspaceSortKey = 'NAME' | 'CREATED' | 'ACTIVITY' | 'ACCESSED';
export type WorkspaceTemplate = 'BLANK' | 'PROJECT' | 'TEAM' | 'PERSONAL';
export const WORKSPACE_TEMPLATES: WorkspaceTemplate[] = ['BLANK', 'PROJECT', 'TEAM', 'PERSONAL'];

export interface DocumentWorkspaceListQuery {
  page?: number;
  size?: number;
  search?: string;
  role?: DocumentWorkspaceRole;
  status?: DocumentWorkspaceStatus;
  ownership?: WorkspaceOwnership;
  pinned?: boolean;
  createdFrom?: string;
  createdTo?: string;
  activeFrom?: string;
  activeTo?: string;
  sort?: WorkspaceSortKey;
  desc?: boolean;
  /** Include member/content/storage numbers (detailed cards). */
  stats?: boolean;
  [key: string]: string | number | boolean | undefined;
}

export interface CreateDocumentWorkspaceRequest {
  name: string;
  description?: string;
  color?: string;
  language?: string;
  template?: WorkspaceTemplate;
}

/** What deleting a workspace would take with it. */
export interface WorkspaceImpact {
  readonly folders: number;
  readonly documents: number;
  readonly files: number;
  readonly usedBytes: number;
  readonly members: number;
  readonly pendingAccessRequests: number;
}

export type RequestStatus = 'PENDING' | 'APPROVED' | 'DENIED';

/** What a signed-in non-member sees of a workspace they followed a link to. */
export interface AccessPreview {
  readonly workspaceId: string;
  readonly name: string;
  readonly color: string | null;
  readonly member: boolean;
  readonly requestStatus: RequestStatus | null;
  readonly requestedAt: string | null;
}

export interface AccessRequest {
  readonly id: string;
  readonly workspaceId: string;
  readonly userId: string;
  readonly email: string;
  readonly message: string | null;
  readonly status: RequestStatus;
  readonly createdAt: string;
  readonly decidedAt: string | null;
}

export interface StorageRequest {
  readonly id: string;
  readonly workspaceId: string;
  readonly workspaceName: string;
  readonly requestedBy: string;
  readonly requestedByEmail: string | null;
  readonly requestedMb: number;
  readonly message: string | null;
  readonly status: RequestStatus;
  readonly grantedMb: number | null;
  readonly createdAt: string;
  readonly handledAt: string | null;
  readonly usedBytes: number;
  readonly quotaMb: number | null;
}

/** One thing somebody else did in one of the caller's workspaces. */
export interface WorkspaceActivityItem {
  readonly id: string;
  readonly workspaceId: string;
  readonly workspaceName: string;
  readonly action: string;
  readonly actorId: string | null;
  readonly actorEmail: string | null;
  readonly actorName: string | null;
  readonly resourceType: string | null;
  readonly resourceId: string | null;
  readonly resourceName: string | null;
  readonly occurredAt: string;
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

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED';

/** An offer to join a workspace with a role; the invited person has to accept it. */
export interface WorkspaceInvitation {
  readonly id: string;
  readonly workspaceId: string;
  readonly workspaceName: string;
  readonly workspaceColor: string | null;
  readonly email: string;
  readonly displayName: string | null;
  readonly role: DocumentWorkspaceRole;
  readonly invitedByEmail: string | null;
  readonly status: InvitationStatus;
  readonly createdAt: string;
  readonly expiresAt: string;
}
