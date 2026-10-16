import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { environment } from '@env/environment';
import { TokenStorageService } from '@core/services/authentication/token-storage.service';

/** Awareness/presence frame broadcast by the backend. */
export interface AwarenessBroadcast {
  clientId: string;
  payload: unknown;
  cursor: string | null;
  userId: string;
  email: string;
}

export interface OperationsBroadcast {
  clientId: string;
  documentId: string;
  version: number;
  operations: unknown[];
  userId: string;
}

/**
 * Thin STOMP client over the backend `/ws` endpoint. The JWT is sent on CONNECT
 * as an `Authorization` native header (matching the REST contract). Consumers
 * subscribe to per-document awareness/operations streams; the client
 * auto-reconnects and re-subscribes.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly tokens = inject(TokenStorageService);

  /** Stable per-tab client id so a client can ignore its own echoes. */
  readonly clientId = crypto.randomUUID();

  private client?: Client;
  private readonly subs = new Map<string, StompSubscription>();

  readonly connected = signal(false);
  readonly awareness$ = new Subject<AwarenessBroadcast>();
  readonly operations$ = new Subject<OperationsBroadcast>();

  connect(): void {
    if (this.client?.active) {
      return;
    }
    this.client = new Client({
      brokerURL: environment.wsUrl,
      connectHeaders: { Authorization: `Bearer ${this.tokens.accessToken ?? ''}` },
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => this.connected.set(true),
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    this.client.activate();
  }

  /** Subscribe to a document's awareness + operations channels. */
  joinDocument(documentWorkspaceId: string, documentId: string): void {
    if (!this.client?.active) {
      this.connect();
    }
    const base = `/topic/workspaces/${documentWorkspaceId}/documents/${documentId}`;

    this.subscribe(`${base}/awareness`, (msg) =>
      this.awareness$.next(JSON.parse(msg.body) as AwarenessBroadcast),
    );
    this.subscribe(`${base}/operations`, (msg) =>
      this.operations$.next(JSON.parse(msg.body) as OperationsBroadcast),
    );
  }

  leaveDocument(documentWorkspaceId: string, documentId: string): void {
    const base = `/topic/workspaces/${documentWorkspaceId}/documents/${documentId}`;
    this.unsubscribe(`${base}/awareness`);
    this.unsubscribe(`${base}/operations`);
  }

  /** Broadcast cursor/selection awareness (ephemeral, not persisted). */
  sendAwareness(documentWorkspaceId: string, documentId: string, cursor: string | null, payload: unknown): void {
    this.client?.publish({
      destination: `/app/workspaces/${documentWorkspaceId}/documents/${documentId}/awareness`,
      body: JSON.stringify({ clientId: this.clientId, cursor, payload }),
    });
  }

  private subscribe(destination: string, handler: (msg: IMessage) => void): void {
    if (this.subs.has(destination) || !this.client) {
      return;
    }
    const attempt = () => {
      if (this.client?.connected) {
        this.subs.set(destination, this.client.subscribe(destination, handler));
      } else {
        setTimeout(attempt, 500);
      }
    };
    attempt();
  }

  private unsubscribe(destination: string): void {
    this.subs.get(destination)?.unsubscribe();
    this.subs.delete(destination);
  }

  disconnect(): void {
    this.subs.forEach((s) => s.unsubscribe());
    this.subs.clear();
    this.client?.deactivate();
    this.connected.set(false);
  }
}
