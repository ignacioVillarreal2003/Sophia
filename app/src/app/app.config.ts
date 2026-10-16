import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {
  PreloadAllModules,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withPreloading,
  withViewTransitions,
} from '@angular/router';
import {provideServiceWorker} from '@angular/service-worker';
import {firstValueFrom} from 'rxjs';
import {environment} from '@env/environment';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {authenticationInterceptor} from '@core/interceptors/authentication/authentication.interceptor';
import {
  correlationInterceptor,
  errorInterceptor,
  retryInterceptor,
} from '@core/interceptors';
import {routes} from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),

    provideHttpClient(
      withInterceptors([
        correlationInterceptor,
        errorInterceptor,
        retryInterceptor,
        authenticationInterceptor,
      ]),
    ),

    provideRouter(
      routes,
      withComponentInputBinding(),
      withViewTransitions(),
      withInMemoryScrolling({scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled'}),
      withPreloading(PreloadAllModules),
    ),

    provideAppInitializer(() => firstValueFrom(inject(AuthenticationService).bootstrap())),

    provideServiceWorker('ngsw-worker.js', {
      enabled: environment.enableServiceWorker,
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
