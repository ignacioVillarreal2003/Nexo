export interface AppEnvironment {
  readonly production: boolean;
  readonly appName: string;
  readonly authenticationApiUrl: string;
  readonly notificationApiUrl: string;
  readonly topicApiUrl: string;
  readonly subscriptionApiUrl: string;
  readonly contentApiUrl: string;
  readonly deliveryApiUrl: string;
  readonly collectorApiUrl: string;
  /** Vacío desactiva el inicio de sesión con Google. */
  readonly googleClientId: string;
  readonly defaultLocale: string;
  readonly supportedLocales: readonly string[];
  readonly accessTokenSkewSeconds: number;
  readonly enableServiceWorker: boolean;
}
