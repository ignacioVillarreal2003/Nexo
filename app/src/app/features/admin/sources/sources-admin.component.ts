import {ChangeDetectionStrategy, Component, OnInit, inject, signal} from '@angular/core';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {clientPagination} from '@shared/utils/client-pagination';
import {JsonPipe} from '@angular/common';
import {AppError} from '@core/models/api.model';
import {Source} from '@core/models/catalog';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {NexoAdminService} from '../data/nexo-admin.service';
import {statusTone} from '../nexo-ui/status-tone';
import {SourceDialogComponent} from './source-dialog.component';

/** Fuentes de información. Se vinculan a los tópicos desde el catálogo. */
@Component({
  selector: 'app-sources-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [JsonPipe, PageHeaderComponent, ButtonComponent, CardComponent, BadgeComponent, SpinnerComponent,
    PaginationComponent, TranslatePipe],
  templateUrl: './sources-admin.component.html',
  styleUrls: ['../nexo-ui/admin-table.scss', './sources-admin.component.scss'],
})
export class SourcesAdminComponent implements OnInit {
  private readonly admin = inject(NexoAdminService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  readonly sources = signal<Source[]>([]);
  readonly pager = clientPagination(this.sources);
  readonly loading = signal(true);
  readonly tone = statusTone;

  ngOnInit(): void {
    this.load();
  }

  edit(source: Source | null): void {
    const ref = this.dialog.open<SourceDialogComponent, Source | null, boolean>(SourceDialogComponent, {data: source});
    ref.closed.subscribe((saved) => saved && this.load());
  }

  toggle(source: Source): void {
    this.admin.sourceStatus(source.id, source.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE').subscribe({
      next: (updated) => this.sources.update((list) => list.map((s) => (s.id === updated.id ? updated : s))),
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  private load(): void {
    this.admin.sources().subscribe({
      next: (sources) => {
        this.sources.set(sources);
        this.loading.set(false);
      },
      error: (e: AppError) => {
        this.loading.set(false);
        this.toast.error(e.message);
      },
    });
  }
}
