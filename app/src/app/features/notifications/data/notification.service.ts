import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject, map, tap } from 'rxjs';
import { ApiService } from '@core/services/notification/api.service';
import { TokenStorageService } from '@core/services/authentication/token-storage.service';
import { environment } from '@env/environment';
import {
  AppNotification,
  NotificationDispatch,
  NotificationGroup,
  NotificationPreferences,
  NotificationPreferencesUpdate,
  NotificationQuery,
} from '@core/models/notifications';
import { PageResponse } from '@core/models/api.model';

const STREAM_RECONNECT_DELAY_MS = 3000;

/** Name of the server-sent event carrying a notification; anything else is ignored. */
const NOTIFICATION_EVENT = 'notification';

export type StreamStatus = 'idle' | 'connecting' | 'live' | 'reconnecting';

/**
 * Client for the notification microservice: inbox, preferences, and a
 * realtime stream. The stream is consumed with `fetch` instead of the native
 * `EventSource`, since `EventSource` cannot attach an `Authorization` header
 * and the backend does not accept the access token as a query parameter.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiService);
  private readonly tokens = inject(TokenStorageService);

  private readonly _unread = signal(0);
  readonly unreadCount = this._unread.asReadonly();

  private readonly _streamStatus = signal<StreamStatus>('idle');
  readonly streamStatus = this._streamStatus.asReadonly();

  private readonly _incoming = new Subject<AppNotification>();
  readonly incoming$ = this._incoming.asObservable();

  private streamController: AbortController | null = null;

  list(page = 0, size = 20, query: NotificationQuery = {}): Observable<PageResponse<AppNotification>> {
    return this.api.getPage<AppNotification>('/api/v1/notifications', {
      page,
      size,
      ...this.searchParams(query),
    });
  }

  groups(unreadOnly = false): Observable<NotificationGroup[]> {
    return this.api.get<NotificationGroup[]>('/api/v1/notifications/groups', { unreadOnly });
  }

  dispatches(id: string): Observable<NotificationDispatch[]> {
    return this.api.get<NotificationDispatch[]>(`/api/v1/notifications/${id}/dispatches`);
  }

  refreshUnread(): Observable<number> {
    return this.api.get<{ unread: number }>('/api/v1/notifications/unread-count').pipe(
      map((res) => res.unread),
      tap((count) => this._unread.set(count)),
    );
  }

  markRead(id: string): Observable<AppNotification> {
    return this.api
      .post<AppNotification>(`/api/v1/notifications/${id}/read`)
      .pipe(tap(() => this.decrementUnread()));
  }

  markAllRead(): Observable<number> {
    return this.api.post<{ marked: number }>('/api/v1/notifications/read-all').pipe(
      map((res) => res.marked),
      tap(() => this._unread.set(0)),
    );
  }

  markGroupRead(groupKey: string): Observable<number> {
    return this.api
      .post<{ marked: number }>(`/api/v1/notifications/groups/${encodeURIComponent(groupKey)}/read`)
      .pipe(
        map((res) => res.marked),
        tap((marked) => this._unread.update((n) => Math.max(0, n - marked))),
      );
  }

  /** @param wasUnread keeps the badge in sync, since deleting an unread notification clears it too. */
  remove(id: string, wasUnread = false): Observable<void> {
    return this.api.delete<void>(`/api/v1/notifications/${id}`).pipe(
      tap(() => {
        if (wasUnread) {
          this.decrementUnread();
        }
      }),
    );
  }

  preferences(): Observable<NotificationPreferences> {
    return this.api.get<NotificationPreferences>('/api/v1/notification-preferences');
  }

  updatePreferences(patch: NotificationPreferencesUpdate): Observable<NotificationPreferences> {
    return this.api.put<NotificationPreferences>('/api/v1/notification-preferences', patch);
  }

  /** Opens the realtime stream; a no-op if already connected. */
  connectStream(): void {
    if (this.streamController) {
      return;
    }
    this.streamController = new AbortController();
    this._streamStatus.set('connecting');
    void this.streamLoop(this.streamController.signal);
  }

  disconnectStream(): void {
    this.streamController?.abort();
    this.streamController = null;
    this._streamStatus.set('idle');
  }

  private decrementUnread(): void {
    this._unread.update((n) => Math.max(0, n - 1));
  }

  private searchParams(query: NotificationQuery): Record<string, string | number | boolean | undefined> {
    return {
      unreadOnly: query.unreadOnly || undefined,
      category: query.category,
      type: query.type,
      severity: query.severity,
      groupKey: query.groupKey,
      search: query.search,
      from: query.from,
      to: query.to,
    };
  }

  private async streamLoop(signal: AbortSignal): Promise<void> {
    while (!signal.aborted) {
      try {
        await this.consumeStream(signal);
      } catch {
        // Connection dropped or was rejected; fall through to reconnect.
      }
      if (!signal.aborted) {
        this._streamStatus.set('reconnecting');
        await new Promise((resolve) => setTimeout(resolve, STREAM_RECONNECT_DELAY_MS));
      }
    }
  }

  private async consumeStream(signal: AbortSignal): Promise<void> {
    const token = this.tokens.accessToken;
    if (!token) {
      return;
    }
    const response = await fetch(`${environment.notificationApiUrl}/api/v1/notifications/stream`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      signal,
    });
    if (!response.ok || !response.body) {
      return;
    }
    this._streamStatus.set('live');
    // The inbox may have moved on while the stream was down.
    this.refreshUnread().subscribe({ error: () => undefined });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (done) {
        return;
      }
      buffer += decoder.decode(value, { stream: true });
      const frames = buffer.split('\n\n');
      buffer = frames.pop() ?? '';
      for (const frame of frames) {
        this.handleFrame(frame);
      }
    }
  }

  private handleFrame(frame: string): void {
    const lines = frame.split('\n');
    const event = lines.find((line) => line.startsWith('event:'))?.slice('event:'.length).trim();
    if (event !== NOTIFICATION_EVENT) {
      // Heartbeat comments and any future event type stay out of the inbox.
      return;
    }
    const data = lines
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice('data:'.length).trim())
      .join('');
    if (!data) {
      return;
    }
    try {
      const notification = JSON.parse(data) as AppNotification;
      this._unread.update((n) => n + 1);
      this._incoming.next(notification);
    } catch {
      // Malformed frame; ignore and keep the connection open.
    }
  }
}
