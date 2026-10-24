import {AppEnvironment} from './environment.model';

export const environment: AppEnvironment = {
  production: true,
  appName: 'Sophia',
  authenticationApiUrl: '/auth',
  documentWorkspaceApiUrl: '/api',
  notificationApiUrl: '/notifications',
  aiApiUrl: '/ai',
  googleClientId: '9579187288-nsihbfdrsk1oe6bv3i8m108j0dbin38l.apps.googleusercontent.com',
  wsUrl: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
  defaultLocale: 'es',
  supportedLocales: ['es', 'en'],
  accessTokenSkewSeconds: 30,
  enableServiceWorker: true,
};
