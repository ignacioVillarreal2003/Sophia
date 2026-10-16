export interface FileVersion {
  readonly id: string;
  readonly versionNumber: number;
  readonly sizeBytes: number;
  readonly mimeType: string;
  readonly createdByName?: string;
  readonly createdAt: string;
}

export interface AuditEntry {
  readonly id: string;
  readonly action: string;
  readonly actorId: string;
  readonly actorName?: string;
  readonly resourceType: string;
  readonly resourceId: string;
  readonly clientIp: string | null;
  readonly metadata: Record<string, unknown>;
  readonly createdAt: string;
}
