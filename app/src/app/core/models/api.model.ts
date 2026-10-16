export interface ApiResponse<T> {
  readonly success: boolean;
  readonly data: T;
  readonly message: string | null;
  readonly timestamp: string;
  readonly requestId: string;
}

export interface PageResponse<T> {
  readonly content: T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
  [key: string]: string | number | boolean | undefined;
}

export function emptyPage<T>(size = 20): PageResponse<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0, first: true, last: true };
}

export interface BulkOperationResponse {
  readonly successCount: number;
  readonly failureCount: number;
  readonly failures: ReadonlyArray<{ readonly id: string; readonly reason: string }>;
}

export interface AppError {
  readonly status: number;
  readonly code: string;
  readonly message: string;
  readonly fieldErrors?: Record<string, string>;
  readonly requestId?: string;
  readonly retryable: boolean;
}
