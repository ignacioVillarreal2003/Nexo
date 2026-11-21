import {Routes} from '@angular/router';
import {authenticationGuard} from '@core/guards/authentication';
import {AUTHENTICATION_ROUTES} from '@features/authentication/authentication.routes';
import {SETTINGS_PUBLIC_ROUTES} from '@features/settings/settings.routes';

export const routes: Routes = [
  ...AUTHENTICATION_ROUTES,
  ...SETTINGS_PUBLIC_ROUTES,
  {
    path: '',
    canActivate: [authenticationGuard],
    loadComponent: () => import('@layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'notifications',
        loadComponent: () =>
          import('@features/notifications/notifications.component').then(
            (m) => m.NotificationsComponent,
          ),
      },
      {
        path: 'settings',
        loadChildren: () =>
          import('@features/settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
      },
      {
        path: 'help',
        loadComponent: () => import('@features/help/help.component').then((m) => m.HelpComponent),
      },
      {
        path: 'admin',
        loadChildren: () => import('@features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        // Mi día, explorar, mis tópicos, favoritos e historial comparten el riel de la sección.
        path: '',
        loadChildren: () => import('@features/feed/feed.routes').then((m) => m.FEED_ROUTES),
      },
    ],
  },
  {
    path: 'error/403',
    data: {
      titleKey: 'errors.forbidden.title',
      descriptionKey: 'errors.forbidden.description',
      actionLabelKey: 'errors.forbidden.backHome',
      actionLink: '/',
    },
    loadComponent: () =>
      import('@features/errors/error-page.component').then((m) => m.ErrorPageComponent),
  },
  {
    path: 'error/404',
    data: {
      titleKey: 'errors.notFound.title',
      descriptionKey: 'errors.notFound.description',
      actionLabelKey: 'errors.notFound.goBack',
    },
    loadComponent: () =>
      import('@features/errors/error-page.component').then((m) => m.ErrorPageComponent),
  },
  {
    path: '**',
    data: {
      titleKey: 'errors.notFound.title',
      descriptionKey: 'errors.notFound.description',
      actionLabelKey: 'errors.notFound.goBack',
    },
    loadComponent: () =>
      import('@features/errors/error-page.component').then((m) => m.ErrorPageComponent),
  },
];
