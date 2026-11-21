import {ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterOutlet} from '@angular/router';
import {CdkScrollable} from '@angular/cdk/scrolling';
import {ToastService} from '@core/services/toast.service';
import {NotificationService} from '@features/notifications/data/notification.service';
import {AppHeaderComponent} from './header/app-header.component';

/**
 * Marco de la app autenticada: cabecera y contenido. Cada sección (Mi día, ajustes, administración)
 * trae su propio riel lateral desde su layout.
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, CdkScrollable, AppHeaderComponent],
  template: `
    <div class="shell">
      <app-header/>

      <div class="shell__body shell__body--no-sidebar">
        <!--
          cdkScrollable registra este contenedor en el ScrollDispatcher del CDK: la ventana nunca
          hace scroll, así que sin él los overlays (menús, tooltips) no siguen a su disparador.
        -->
        <main cdkScrollable class="shell__main">
          <router-outlet/>
        </main>
      </div>
    </div>
  `,
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit, OnDestroy {
  private readonly notifications = inject(NotificationService);
  private readonly toast = inject(ToastService);

  constructor() {
    this.notifications.incoming$.pipe(takeUntilDestroyed()).subscribe((notification) => {
      this.toast.show(notification.title, 'info');
    });
  }

  ngOnInit(): void {
    this.notifications.refreshUnread().subscribe();
    this.notifications.connectStream();
  }

  ngOnDestroy(): void {
    this.notifications.disconnectStream();
  }
}
