import {Routes} from '@angular/router';
import {permissionGuard} from '@core/guards/authentication';
import {ADMIN_PERMISSIONS} from '@core/models/admin';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard(...ADMIN_PERMISSIONS)],
    loadComponent: () =>
      import('./admin-layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    children: [
      {path: '', pathMatch: 'full', loadComponent: () => import('./admin-home.component').then((m) => m.AdminHomeComponent)},
      {
        path: 'pipeline',
        canActivate: [permissionGuard('PIPELINE_READ', 'DELIVERY_MANAGE', 'STATISTICS_READ')],
        loadComponent: () =>
          import('./pipeline/pipeline-admin.component').then((m) => m.PipelineAdminComponent),
      },
      {
        path: 'catalog',
        canActivate: [permissionGuard('TOPIC_MANAGE')],
        loadComponent: () => import('./catalog/catalog-admin.component').then((m) => m.CatalogAdminComponent),
      },
      {
        path: 'sources',
        canActivate: [permissionGuard('TOPIC_MANAGE')],
        loadComponent: () => import('./sources/sources-admin.component').then((m) => m.SourcesAdminComponent),
      },
      {
        path: 'contents',
        canActivate: [permissionGuard('CONTENT_REVIEW')],
        loadComponent: () => import('./contents/contents-admin.component').then((m) => m.ContentsAdminComponent),
      },
      {
        path: 'users',
        canActivate: [permissionGuard('USER_READ')],
        loadComponent: () =>
          import('./users/users-list.component').then((m) => m.UsersListComponent),
      },
      {
        path: 'users/new',
        canActivate: [permissionGuard('USER_CREATE')],
        loadComponent: () =>
          import('./users/user-create.component').then((m) => m.UserCreateComponent),
      },
      {
        path: 'users/:id',
        canActivate: [permissionGuard('USER_READ')],
        loadComponent: () =>
          import('./users/user-detail.component').then((m) => m.UserDetailComponent),
      },
      {
        path: 'roles',
        canActivate: [permissionGuard('ROLE_READ')],
        loadComponent: () =>
          import('./roles/roles-list.component').then((m) => m.RolesListComponent),
      },
      {
        path: 'roles/new',
        canActivate: [permissionGuard('ROLE_MANAGE')],
        loadComponent: () => import('./roles/role-form.component').then((m) => m.RoleFormComponent),
      },
      {
        path: 'roles/:id',
        canActivate: [permissionGuard('ROLE_MANAGE')],
        loadComponent: () => import('./roles/role-form.component').then((m) => m.RoleFormComponent),
      },
      {
        path: 'permissions',
        canActivate: [permissionGuard('PERMISSION_READ')],
        loadComponent: () =>
          import('./permissions/permissions-list.component').then(
            (m) => m.PermissionsListComponent,
          ),
      },
      {
        path: 'permissions/new',
        canActivate: [permissionGuard('PERMISSION_MANAGE')],
        loadComponent: () =>
          import('./permissions/permission-form.component').then((m) => m.PermissionFormComponent),
      },
      {
        path: 'permissions/:id',
        canActivate: [permissionGuard('PERMISSION_MANAGE')],
        loadComponent: () =>
          import('./permissions/permission-form.component').then((m) => m.PermissionFormComponent),
      },
      {
        path: 'notifications',
        canActivate: [
          permissionGuard('NOTIFICATION_CATEGORY_READ', 'NOTIFICATION_AUDIT_READ', 'NOTIFICATION_DEAD_LETTER_REPLAY'),
        ],
        loadComponent: () =>
          import('./notifications/notifications-admin.component').then(
            (m) => m.NotificationsAdminComponent,
          ),
      },
      {
        path: 'audit',
        canActivate: [permissionGuard('AUDIT_READ')],
        loadComponent: () => import('./audit/audit.component').then((m) => m.AuditComponent),
      },
      {
        path: 'service-clients',
        canActivate: [permissionGuard('SERVICE_CLIENT_MANAGE')],
        loadComponent: () =>
          import('./service-clients/service-clients-list.component').then(
            (m) => m.ServiceClientsListComponent,
          ),
      },
      {
        path: 'service-clients/new',
        canActivate: [permissionGuard('SERVICE_CLIENT_MANAGE')],
        loadComponent: () =>
          import('./service-clients/service-client-form.component').then(
            (m) => m.ServiceClientFormComponent,
          ),
      },
      {
        path: 'service-clients/:id',
        canActivate: [permissionGuard('SERVICE_CLIENT_MANAGE')],
        loadComponent: () =>
          import('./service-clients/service-client-form.component').then(
            (m) => m.ServiceClientFormComponent,
          ),
      },
    ],
  },
];
