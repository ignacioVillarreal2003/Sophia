import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '@env/environment';
import { TokenStorageService } from '@core/services/authentication/token-storage.service';
import { authenticationInterceptor } from './authentication.interceptor';

const TOKEN = 'test-access-token';

/**
 * Every Sophia API has to receive the bearer token. This is the file that gets
 * forgotten when a new service is added: the calls then go out anonymous and
 * come back 401, which surfaces in the UI as "the module does not load" rather
 * than as an auth error.
 */
describe('authenticationInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([authenticationInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(TokenStorageService).set({ accessToken: TOKEN } as never);
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    TestBed.inject(TokenStorageService).clear();
    controller.verify();
  });

  // Derived from the environment, so a new *ApiUrl automatically gets covered.
  const apiUrls = Object.entries(environment)
    .filter(([key, value]) => key.endsWith('ApiUrl') && typeof value === 'string' && value)
    .map(([key, value]) => [key, value as string] as const);

  it('knows about every API declared in the environment', () => {
    expect(apiUrls.length).toBeGreaterThanOrEqual(3);
  });

  for (const [key, base] of apiUrls) {
    it(`attaches the bearer token to ${key} requests`, () => {
      http.get(`${base}/api/v1/ping`).subscribe();

      const request = controller.expectOne(`${base}/api/v1/ping`);
      expect(
        request.request.headers.get('Authorization'),
        `${key} requests go out without a token`,
      ).toBe(`Bearer ${TOKEN}`);
      request.flush({});
    });
  }

  it('leaves third-party requests alone', () => {
    http.get('https://example.com/thing').subscribe();

    const request = controller.expectOne('https://example.com/thing');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
});
