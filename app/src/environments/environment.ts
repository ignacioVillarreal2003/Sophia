import {AppEnvironment} from './environment.model';

export const environment: AppEnvironment = {
  production: false,
  appName: 'Sophia',
  authenticationApiUrl: 'http://localhost:8080',
  documentWorkspaceApiUrl: 'http://localhost:8081',
  notificationApiUrl: 'http://localhost:8082',
  aiApiUrl: 'http://localhost:8083',
  googleClientId: '9579187288-nsihbfdrsk1oe6bv3i8m108j0dbin38l.apps.googleusercontent.com',
  wsUrl: 'ws://localhost:8081/ws',
  defaultLocale: 'es',
  supportedLocales: ['es', 'en'],
  accessTokenSkewSeconds: 30,
  enableServiceWorker: false,
};
