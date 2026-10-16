import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/notification/api.service';
import { PageResponse } from '@core/models/api.model';
import {
  AppNotification,
  DeadLetterReplayResult,
  NotificationAuditEntry,
  NotificationAuditQuery,
  NotificationCategoryCreate,
  NotificationCategoryInfo,
  NotificationCategoryUpdate,
  NotificationPreferences,
  NotificationPreferencesUpdate,
  NotificationQuery,
} from '@core/models/notifications';

/**
 * Privileged half of the notification API: the category catalogue, other
 * users' inboxes and preferences, the audit trail and dead letter recovery.
 * Every endpoint here needs a permission the USER role does not carry.
 */
@Injectable({ providedIn: 'root' })
export class NotificationAdminService {
  private readonly api = inject(ApiService);

  categories(page = 0, size = 100): Observable<PageResponse<NotificationCategoryInfo>> {
    return this.api.getPage<NotificationCategoryInfo>('/api/v1/notification-categories', {
      page,
      size,
      sort: 'code,asc',
    });
  }

  createCategory(body: NotificationCategoryCreate): Observable<NotificationCategoryInfo> {
    return this.api.post<NotificationCategoryInfo>('/api/v1/notification-categories', body);
  }

  updateCategory(code: string, body: NotificationCategoryUpdate): Observable<NotificationCategoryInfo> {
    return this.api.patch<NotificationCategoryInfo>(
      `/api/v1/notification-categories/${encodeURIComponent(code)}`,
      body,
    );
  }

  deleteCategory(code: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/notification-categories/${encodeURIComponent(code)}`);
  }

  /** Reading another user's inbox is written to the notification audit trail. */
  userNotifications(
    userId: string,
    page = 0,
    size = 10,
    query: NotificationQuery = {},
  ): Observable<PageResponse<AppNotification>> {
    return this.api.getPage<AppNotification>(`/api/v1/users/${userId}/notifications`, {
      page,
      size,
      unreadOnly: query.unreadOnly || undefined,
      category: query.category,
      severity: query.severity,
      search: query.search,
    });
  }

  userPreferences(userId: string): Observable<NotificationPreferences> {
    return this.api.get<NotificationPreferences>(`/api/v1/users/${userId}/notification-preferences`);
  }

  updateUserPreferences(
    userId: string,
    patch: NotificationPreferencesUpdate,
  ): Observable<NotificationPreferences> {
    return this.api.put<NotificationPreferences>(
      `/api/v1/users/${userId}/notification-preferences`,
      patch,
    );
  }

  auditLog(page = 0, size = 50, query: NotificationAuditQuery = {}): Observable<PageResponse<NotificationAuditEntry>> {
    return this.api.getPage<NotificationAuditEntry>('/api/v1/audit-log', {
      page,
      size,
      userId: query.userId,
      action: query.action,
      resourceType: query.resourceType,
      resourceId: query.resourceId,
      result: query.result,
      requestId: query.requestId,
      from: query.from,
      to: query.to,
    });
  }

  replayDeadLetters(topic: string, max: number): Observable<DeadLetterReplayResult> {
    return this.api.post<DeadLetterReplayResult>(
      `/api/v1/dead-letters/replay?topic=${encodeURIComponent(topic)}&max=${max}`,
    );
  }
}
