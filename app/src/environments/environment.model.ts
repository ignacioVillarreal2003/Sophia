export interface AppEnvironment {
  readonly production: boolean;
  readonly appName: string;
  readonly authenticationApiUrl: string;
  readonly documentWorkspaceApiUrl: string;
  readonly notificationApiUrl: string;
  readonly googleClientId: string;
  readonly wsUrl: string;
  readonly defaultLocale: string;
  readonly supportedLocales: readonly string[];
  readonly accessTokenSkewSeconds: number;
  readonly enableServiceWorker: boolean;
}
