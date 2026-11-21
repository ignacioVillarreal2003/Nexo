import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {Router} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';

/** Entrada al panel: lleva a la primera sección para la que el usuario tiene permiso. */
const LANDINGS: ReadonlyArray<{link: string; permissions: string[]}> = [
  {link: 'pipeline', permissions: ['PIPELINE_READ', 'DELIVERY_MANAGE', 'STATISTICS_READ']},
  {link: 'catalog', permissions: ['TOPIC_MANAGE']},
  {link: 'contents', permissions: ['CONTENT_REVIEW']},
  {link: 'users', permissions: ['USER_READ']},
  {link: 'audit', permissions: ['AUDIT_READ']},
];

@Component({
  selector: 'app-admin-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class AdminHomeComponent {
  constructor() {
    const permissions = inject(AuthenticationService).permissions();
    const landing = LANDINGS.find((l) => l.permissions.some((p) => permissions.has(p)));
    inject(Router).navigate(['/admin', landing?.link ?? 'users'], {replaceUrl: true});
  }
}
