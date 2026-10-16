import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '@env/environment';
import { AuthResponse, AuthSession, AuditLogEntry, ChangeEmailRequest, ChangePasswordRequest, ConfirmEmailChangeRequest, DeleteAccountRequest, ForgotPasswordRequest, GoogleLinkRequest, GoogleLoginRequest, LoginRequest, RegisterRequest, ResendVerificationRequest, ResetPasswordRequest, Session, User } from '@core/models/authentication';
import { ApiResponse, AppError, PageQuery, PageResponse } from '@core/models/api.model';
import {
  Observable,
  ReplaySubject,
  TimeoutError,
  catchError,
  map,
  of,
  switchMap,
  tap,
  throwError,
  timeout,
} from 'rxjs';
import { skipAuth } from '@core/services/http-context';
import { TokenStorageService } from './token-storage.service';
import { TranslationService } from '@core/services/i18n/translation.service';
import { ThemeService } from '@core/services/theme.service';
import { ApiService } from './api.service';

const GOOGLE_LOGIN_TIMEOUT_MS = 20000;

@Injectable({ providedIn: 'root' })
export class AuthenticationService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private readonly storage = inject(TokenStorageService);
  private readonly i18n = inject(TranslationService);
  private readonly themeService = inject(ThemeService);
  private readonly base = `${environment.authenticationApiUrl}/api/v1/authentication`;

  private readonly _user = signal<User | null>(null);
  private readonly _initialised = signal(false);

  readonly user = this._user.asReadonly();
  readonly initialised = this._initialised.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly permissions = computed(() => new Set(this._user()?.permissions ?? []));
  readonly roles = computed(() => new Set(this._user()?.roles ?? []));

  private refresh$: ReplaySubject<string> | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.storage.externalChange.subscribe((session) => {
      if (!session) {
        this.clearProactiveRefresh();
        this._user.set(null);
        return;
      }
      this.scheduleProactiveRefresh(session.expiresAt);
      this.refreshUser().subscribe({ error: () => {} });
    });
  }

  bootstrap(): Observable<User | null> {
    const session = this.storage.get();
    if (!session) {
      this._initialised.set(true);
      return of(null);
    }
    return this.refreshUser().pipe(
      tap(() => this.scheduleProactiveRefresh(session.expiresAt)),
      catchError(() => {
        this.storage.clear();
        this._user.set(null);
        return of(null);
      }),
      tap(() => this._initialised.set(true)),
    );
  }

  login(request: LoginRequest): Observable<User> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.base}/login`, request, { context: skipAuth() })
      .pipe(map((res) => this.applyAuth(res.data)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<ApiResponse<User>>(`${this.base}/register`, request, { context: skipAuth() })
      .pipe(map((res) => res.data));
  }

  loginWithGoogle(request: GoogleLoginRequest): Observable<User> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.base}/google`, request, { context: skipAuth() })
      .pipe(
        timeout(GOOGLE_LOGIN_TIMEOUT_MS),
        map((res) => this.applyAuth(res.data)),
        catchError((err: unknown) => {
          if (err instanceof TimeoutError) {
            const timeoutError: AppError = {
              status: 0,
              code: 'GOOGLE_LOGIN_TIMEOUT',
              message: 'No recibimos respuesta de Google. Probá iniciar sesión de nuevo.',
              retryable: true,
            };
            return throwError(() => timeoutError);
          }
          return throwError(() => err);
        }),
      );
  }

  forgotPassword(request: ForgotPasswordRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/forgot-password`, request, { context: skipAuth() })
      .pipe(map(() => void 0));
  }

  resetPassword(request: ResetPasswordRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/reset-password`, request, { context: skipAuth() })
      .pipe(map(() => void 0));
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/change-password`, request)
      .pipe(map(() => void 0));
  }

  verifyEmail(token: string): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/verify-email`, { token }, { context: skipAuth() })
      .pipe(
        switchMap(() =>
          this._user() ? this.refreshUser().pipe(catchError(() => of(null))) : of(null),
        ),
        map(() => void 0),
      );
  }

  resendVerification(request: ResendVerificationRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/resend-verification`, request, { context: skipAuth() })
      .pipe(map(() => void 0));
  }

  logout(): Observable<void> {
    const refreshToken = this.storage.refreshToken;
    const done = () => {
      this.clearProactiveRefresh();
      this.storage.clear();
      this._user.set(null);
    };
    if (!refreshToken) {
      done();
      return of(void 0);
    }
    return this.http.post<ApiResponse<void>>(`${this.base}/logout`, { refreshToken }).pipe(
      catchError(() => of(null)),
      tap(done),
      map(() => void 0),
    );
  }

  logoutAll(): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.base}/logout-all`, {}).pipe(
      tap(() => {
        this.clearProactiveRefresh();
        this.storage.clear();
        this._user.set(null);
      }),
      map(() => void 0),
    );
  }

  changeEmail(request: ChangeEmailRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/change-email`, request)
      .pipe(map(() => void 0));
  }

  confirmEmailChange(request: ConfirmEmailChangeRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/confirm-email-change`, request, {
        context: skipAuth(),
      })
      .pipe(
        switchMap(() => (this._user() ? this.refreshUser().pipe(catchError(() => of(null))) : of(null))),
        map(() => void 0),
      );
  }

  deleteAccount(request: DeleteAccountRequest): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/account`, { body: request })
      .pipe(
        tap(() => {
          this.clearProactiveRefresh();
          this.storage.clear();
          this._user.set(null);
        }),
        map(() => void 0),
      );
  }

  linkGoogle(request: GoogleLinkRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/google/link`, request)
      .pipe(switchMap(() => this.refreshUser()), map(() => void 0));
  }

  unlinkGoogle(): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/google/unlink`)
      .pipe(switchMap(() => this.refreshUser()), map(() => void 0));
  }

  listSessions(): Observable<Session[]> {
    return this.http
      .get<ApiResponse<PageResponse<Session>>>(`${environment.authenticationApiUrl}/api/v1/sessions`, {
        params: { size: 100 },
      })
      .pipe(map((res) => res.data.content));
  }

  revokeSession(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${environment.authenticationApiUrl}/api/v1/sessions/${id}`)
      .pipe(map(() => void 0));
  }

  revokeOtherSessions(): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${environment.authenticationApiUrl}/api/v1/sessions`)
      .pipe(map(() => void 0));
  }

  updatePreferences(patch: {
    theme?: string;
    themeMode?: string;
    locale?: string;
    timezone?: string;
    /** Omit to leave the selection alone; send `[]` to clear it. */
    hiddenModules?: string[];
  }): Observable<User> {
    const id = this._user()?.id;
    if (!id) {
      return throwError(() => new Error('No authenticated user'));
    }
    return this.http
      .patch<ApiResponse<User>>(`${environment.authenticationApiUrl}/api/v1/users/${id}`, patch)
      .pipe(
        map((res) => res.data),
        tap((user) => this._user.set(user)),
      );
  }

  deletePhoto(): Observable<User> {
    const id = this._user()?.id;
    if (!id) {
      return throwError(() => new Error('No authenticated user'));
    }
    return this.http
      .delete<ApiResponse<User>>(`${environment.authenticationApiUrl}/api/v1/users/${id}/photo`)
      .pipe(
        map((res) => res.data),
        tap((user) => this._user.set(user)),
      );
  }

  myActivity(query: PageQuery): Observable<PageResponse<AuditLogEntry>> {
    return this.api.getPage<AuditLogEntry>('/api/v1/audit-logs/me', query);
  }

  refreshAccessToken(): Observable<string> {
    if (this.refresh$) {
      return this.refresh$.asObservable();
    }
    const refreshToken = this.storage.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }

    this.refresh$ = new ReplaySubject<string>(1);
    const subject = this.refresh$;

    this.http
      .post<ApiResponse<AuthResponse>>(
        `${this.base}/refresh`,
        { refreshToken },
        { context: skipAuth() },
      )
      .pipe(map((res) => this.applyAuth(res.data)))
      .subscribe({
        next: () => {
          subject.next(this.storage.accessToken as string);
          subject.complete();
          this.refresh$ = null;
        },
        error: (err) => {
          subject.error(err);
          this.refresh$ = null;
          this.clearProactiveRefresh();
          this.storage.clear();
          this._user.set(null);
        },
      });

    return subject.asObservable();
  }

  updateProfile(patch: { firstName?: string; lastName?: string }): Observable<User> {
    const id = this._user()?.id;
    if (!id) {
      return throwError(() => new Error('No authenticated user'));
    }
    return this.http
      .patch<ApiResponse<User>>(`${environment.authenticationApiUrl}/api/v1/users/${id}`, patch)
      .pipe(
        map((res) => res.data),
        tap((user) => this._user.set(user)),
      );
  }

  uploadPhoto(file: File): Observable<User> {
    const id = this._user()?.id;
    if (!id) {
      return throwError(() => new Error('No authenticated user'));
    }
    const body = new FormData();
    body.append('file', file);
    return this.http
      .patch<ApiResponse<User>>(`${environment.authenticationApiUrl}/api/v1/users/${id}/photo`, body)
      .pipe(
        map((res) => res.data),
        tap((user) => this._user.set(user)),
      );
  }

  refreshUser(): Observable<User> {
    return this.http.get<ApiResponse<User>>(`${this.base}/me`).pipe(
      tap((res) => {
        this._user.set(res.data);
        this.syncPreferences(res.data);
      }),
      map((res) => res.data),
    );
  }

  private syncPreferences(user: User): void {
    if (user.locale) {
      this.i18n.setLocale(user.locale.split('-')[0]);
    }
    this.themeService.applyFromAccount(user.theme, user.themeMode);
  }

  private applyAuth(auth: AuthResponse): User {
    const session: AuthSession = {
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      expiresAt: Date.now() + auth.expiresIn * 1000,
    };
    this.storage.set(session);
    this._user.set(auth.user);
    this.syncPreferences(auth.user);
    this.scheduleProactiveRefresh(session.expiresAt);
    return auth.user;
  }

  private scheduleProactiveRefresh(expiresAt: number): void {
    this.clearProactiveRefresh();
    const skewMs = environment.accessTokenSkewSeconds * 1000;
    const delay = Math.max(expiresAt - Date.now() - skewMs, 0);
    this.refreshTimer = setTimeout(() => {
      this.refreshAccessToken().subscribe({ error: () => {} });
    }, delay);
  }

  private clearProactiveRefresh(): void {
    if (this.refreshTimer !== null) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }
}
