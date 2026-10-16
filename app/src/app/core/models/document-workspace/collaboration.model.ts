import { AccessMode } from './enums';
import { UserRef } from '@core/models/authentication/user.model';

export interface Comment {
  readonly id: string;
  readonly artifactId: string;
  readonly parentId: string | null;
  readonly author: UserRef;
  readonly body: string;
  readonly mentions: string[];
  readonly reactions: Record<string, string[]>;
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
}

export interface AclEntry {
  readonly id: string;
  readonly workspaceId: string;
  readonly resourceId: string;
  readonly subjectUserId: string;
  readonly modes: AccessMode[];
  readonly grantedBy: string;
  readonly expiresAt: string | null;
  readonly createdAt: string;
}

export interface GrantPermissionRequest {
  subjectUserId: string;
  modes: AccessMode[];
  expiresAt?: string;
}

export interface PresenceUser {
  readonly userId: string;
  readonly fullName: string;
  readonly photoUrl?: string | null;
  readonly color: string;
  readonly cursorBlockId?: string | null;
}
