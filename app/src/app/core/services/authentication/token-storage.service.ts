import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { AuthSession } from '@core/models/authentication';

const SESSION_KEY = 'sophia.session';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private cache: AuthSession | null = null;

  readonly externalChange = new Subject<AuthSession | null>();

  constructor() {
    window.addEventListener('storage', (event: StorageEvent) => {
      if (event.key !== SESSION_KEY) {
        return;
      }
      this.cache = event.newValue ? (JSON.parse(event.newValue) as AuthSession) : null;
      this.externalChange.next(this.cache);
    });
  }

  get(): AuthSession | null {
    if (this.cache) {
      return this.cache;
    }
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) {
      return null;
    }
    try {
      this.cache = JSON.parse(raw) as AuthSession;
      return this.cache;
    } catch {
      this.clear();
      return null;
    }
  }

  set(session: AuthSession): void {
    this.cache = session;
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  }

  clear(): void {
    this.cache = null;
    localStorage.removeItem(SESSION_KEY);
  }

  get accessToken(): string | null {
    return this.get()?.accessToken ?? null;
  }

  get refreshToken(): string | null {
    return this.get()?.refreshToken ?? null;
  }
}
