export type AuditResult = 'SUCCESS' | 'FAILURE';

export interface AuditLogEntry {
  readonly id: string;
  readonly userId: string | null;
  readonly userEmail: string | null;
  readonly action: string;
  readonly resourceType: string | null;
  readonly resourceId: string | null;
  readonly details: Record<string, unknown> | null;
  readonly ipAddress: string | null;
  readonly userAgent: string | null;
  readonly result: AuditResult;
  readonly requestId: string | null;
  readonly createdAt: string;
}

export interface AuditLogQuery {
  page?: number;
  size?: number;
  sort?: string;
  userId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  result?: AuditResult;
  requestId?: string;
  from?: string;
  to?: string;
  [key: string]: string | number | undefined;
}
