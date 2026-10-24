export type WorkspaceAuditResult = 'SUCCESS' | 'FAILURE';

/**
 * An entry of a workspace's own audit trail. Unlike the authentication trail,
 * this one is written automatically: one row per committed domain event, so
 * actions read as dotted event types (e.g. `artifact.trashed`). Exact same
 * shape as authentication's and notification's audit trails — same fields,
 * same names. The workspace an entry belongs to is implicit: the endpoint is
 * always queried for one workspace at a time (see WorkspaceAuditQuery), and
 * the backend stores it inside `details.workspaceId` rather than as its own
 * column, same as it does for every other contextual field.
 */
export interface WorkspaceAuditEntry {
  readonly id: string;
  readonly userId: string | null;
  readonly userEmail: string | null;
  readonly action: string;
  readonly resourceType: string | null;
  readonly resourceId: string | null;
  readonly details: Record<string, unknown> | null;
  readonly ipAddress: string | null;
  readonly userAgent: string | null;
  readonly result: WorkspaceAuditResult;
  readonly requestId: string | null;
  readonly createdAt: string;
}

export interface WorkspaceAuditQuery {
  page?: number;
  size?: number;
  sort?: string;
  action?: string;
  userId?: string;
  resourceType?: string;
  resourceId?: string;
  result?: WorkspaceAuditResult;
  requestId?: string;
  /** Part of who did it, what was done, or the name of what it was done to. */
  q?: string;
  from?: string;
  to?: string;
  [key: string]: string | number | undefined;
}
