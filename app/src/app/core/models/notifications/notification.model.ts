/** code = <MICROSERVICE>_<CATEGORY>, e.g. DOCUMENT_WORKSPACE_MENTION. SYSTEM is platform-generated, unprefixed. */
export type NotificationCategory =
  | 'AUTHENTICATION_SECURITY'
  | 'DOCUMENT_WORKSPACE_WORKSPACE'
  | 'DOCUMENT_WORKSPACE_COMMENT'
  | 'DOCUMENT_WORKSPACE_MENTION'
  | 'DOCUMENT_WORKSPACE_SHARING'
  | 'SYSTEM';

/** Categories the user can silence; AUTHENTICATION_SECURITY is always delivered. */
export const CONFIGURABLE_NOTIFICATION_CATEGORIES: NotificationCategory[] = [
  'DOCUMENT_WORKSPACE_WORKSPACE',
  'DOCUMENT_WORKSPACE_COMMENT',
  'DOCUMENT_WORKSPACE_MENTION',
  'DOCUMENT_WORKSPACE_SHARING',
  'SYSTEM',
];

export type NotificationChannel = 'IN_APP' | 'EMAIL';

/** Channels a user can toggle globally or per category. Order drives display order. */
export const NOTIFICATION_CHANNELS: NotificationChannel[] = ['EMAIL', 'IN_APP'];

export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';

export const NOTIFICATION_SEVERITIES: NotificationSeverity[] = [
  'INFO',
  'SUCCESS',
  'WARNING',
  'ERROR',
];

export interface AppNotification {
  readonly id: string;
  readonly createdBy: string | null;
  readonly category: NotificationCategory;
  readonly type: string;
  readonly severity: NotificationSeverity;
  readonly priority: number;
  readonly groupKey: string | null;
  readonly targetType: string | null;
  readonly targetId: string | null;
  readonly title: string;
  readonly body: string | null;
  readonly data: Record<string, string> | null;
  readonly read: boolean;
  readonly expiresAt: string | null;
  readonly createdAt: string;
}

/** Filters accepted by the inbox search endpoint. Blank values are dropped by the API layer. */
export interface NotificationQuery {
  unreadOnly?: boolean;
  category?: string;
  type?: string;
  severity?: string;
  groupKey?: string;
  search?: string;
  from?: string;
  to?: string;
}

/** Notifications sharing a group key, collapsed into one row. */
export interface NotificationGroup {
  readonly groupKey: string;
  readonly total: number;
  readonly unread: number;
  readonly firstAt: string;
  readonly lastAt: string;
}

export type NotificationDispatchStatus = 'PENDING' | 'SENT' | 'FAILED';

/** Per-channel delivery record for one notification. */
export interface NotificationDispatch {
  readonly id: string;
  readonly channel: NotificationChannel;
  readonly status: NotificationDispatchStatus;
  readonly attempts: number;
  readonly lastError: string | null;
  readonly sentAt: string | null;
  readonly failedAt: string | null;
  readonly createdAt: string;
}

/** An entry of the notification category catalogue, as served by the admin endpoint. */
export interface NotificationCategoryInfo {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  /** Mandatory categories ignore user preferences and cannot be silenced. */
  readonly mandatory: boolean;
  /** System categories are seeded by the platform and cannot be deleted. */
  readonly system: boolean;
  readonly createdBy: string | null;
  readonly updatedBy: string | null;
}

export interface NotificationCategoryCreate {
  code: string;
  name: string;
  description?: string | null;
  mandatory?: boolean;
}

export interface NotificationCategoryUpdate {
  name?: string;
  description?: string | null;
  mandatory?: boolean;
}

export interface NotificationPreferences {
  readonly channels: Partial<Record<NotificationChannel, boolean>>;
  readonly categories: Partial<Record<string, Partial<Record<NotificationChannel, boolean>>>>;
}

export interface NotificationPreferencesUpdate {
  channels?: Partial<Record<NotificationChannel, boolean>>;
  categories?: Partial<Record<string, Partial<Record<NotificationChannel, boolean>>>>;
}

export type NotificationAuditResult = 'SUCCESS' | 'FAILURE';

export interface NotificationAuditEntry {
  readonly id: string;
  readonly userId: string | null;
  readonly userEmail: string | null;
  readonly action: string;
  readonly resourceType: string | null;
  readonly resourceId: string | null;
  readonly details: Record<string, unknown> | null;
  readonly ipAddress: string | null;
  readonly userAgent: string | null;
  readonly result: NotificationAuditResult;
  readonly requestId: string | null;
  readonly createdAt: string;
}

export interface NotificationAuditQuery {
  userId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  result?: NotificationAuditResult;
  requestId?: string;
  from?: string;
  to?: string;
}

export interface DeadLetterReplayResult {
  readonly topic: string;
  readonly replayed: number;
}
