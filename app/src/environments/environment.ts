import {AppEnvironment} from './environment.model';

export const environment: AppEnvironment = {
  production: false,
  appName: 'Nexo',
  authenticationApiUrl: 'http://localhost:8090',
  notificationApiUrl: 'http://localhost:8091',
  topicApiUrl: 'http://localhost:8092',
  subscriptionApiUrl: 'http://localhost:8093',
  contentApiUrl: 'http://localhost:8094',
  deliveryApiUrl: 'http://localhost:8095',
  collectorApiUrl: 'http://localhost:8097',
  googleClientId: '982058064899-skq6ijt4gltegkvc6empjpomepeu246g.apps.googleusercontent.com',
  defaultLocale: 'es',
  supportedLocales: ['es', 'en'],
  accessTokenSkewSeconds: 30,
  enableServiceWorker: false,
};
