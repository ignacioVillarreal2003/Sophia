import { AccessMode, ArtifactType } from './enums';

export interface Comment {
  readonly id: string;
  readonly artifactId: string;
  readonly parentCommentId: string | null;
  readonly authorId: string;
  readonly body: string;
  readonly mentions: string[];
  readonly reactions: Record<string, string[]>;
  readonly edited?: boolean;
  readonly resolved: boolean;
  readonly deleted: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly replies?: Comment[];
}

export interface CreateCommentRequest {
  body: string;
  parentId?: string | null;
  mentions?: string[];
}

export interface Tag {
  readonly id: string;
  readonly workspaceId: string;
  readonly name: string;
  readonly color: string;
  readonly parentTagId?: string | null;
  readonly createdAt: string;
}

export interface PresenceUser {
  readonly userId: string;
  readonly fullName: string;
  readonly photoUrl?: string | null;
  readonly color: string;
  readonly cursorBlockId?: string | null;
}

/** A folder or document shared with the caller from a workspace they are not a member of. */
export interface SharedItem {
  readonly aclEntryId: string;
  readonly artifactId: string;
  readonly workspaceId: string;
  readonly workspaceName: string;
  readonly name: string;
  readonly type: ArtifactType;
  readonly modes: AccessMode[];
  readonly sharedByEmail: string | null;
  readonly sharedAt: string;
  readonly expiresAt: string | null;
  readonly workspaceColor: string | null;
  readonly updatedAt: string | null;
}
