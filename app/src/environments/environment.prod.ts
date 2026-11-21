import {AppEnvironment} from './environment.model';

export const environment: AppEnvironment = {
  production: true,
  appName: 'Nexo',
  authenticationApiUrl: '/auth',
  notificationApiUrl: '/notifications',
  topicApiUrl: '/topics',
  subscriptionApiUrl: '/subscriptions',
  contentApiUrl: '/contents',
  deliveryApiUrl: '/deliveries',
  collectorApiUrl: '/collector',
  googleClientId: '',
  defaultLocale: 'es',
  supportedLocales: ['es', 'en'],
  accessTokenSkewSeconds: 30,
  enableServiceWorker: true,
};
