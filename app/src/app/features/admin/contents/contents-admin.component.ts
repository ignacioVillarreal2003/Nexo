import {ChangeDetectionStrategy, Component, OnInit, computed, inject, signal} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {Subject, debounceTime} from 'rxjs';
import {AppError} from '@core/models/api.model';
import {TopicDetail} from '@core/models/catalog';
import {ContentItem, ContentStatus} from '@core/models/pipeline';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {NexoAdminService} from '../data/nexo-admin.service';
import {statusTone} from '../nexo-ui/status-tone';
import {ContentReviewDialogComponent} from './content-review-dialog.component';

/** Revisión de lo recopilado: búsqueda por estado, tópico o texto y corrección manual. */
@Component({
  selector: 'app-contents-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RelativeTimePipe, PageHeaderComponent, CardComponent, BadgeComponent, IconComponent,
    PaginationComponent, SpinnerComponent, TranslatePipe],
  templateUrl: './contents-admin.component.html',
  styleUrls: ['../nexo-ui/admin-table.scss', './contents-admin.component.scss'],
})
export class ContentsAdminComponent implements OnInit {
  private readonly admin = inject(NexoAdminService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);

  readonly statuses: ContentStatus[] = ['PUBLISHED', 'DUPLICATE', 'REJECTED', 'HIDDEN'];
  readonly rows = signal<ContentItem[]>([]);
  readonly topics = signal<TopicDetail[]>([]);
  readonly loading = signal(true);
  readonly status = signal<ContentStatus | ''>('');
  readonly topicId = signal('');
  readonly search = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);
  readonly tone = statusTone;

  private readonly topicNames = computed(() => new Map(this.topics().map((t) => [t.topic.id, t.topic.name])));
  private readonly search$ = new Subject<string>();

  ngOnInit(): void {
    this.admin.topicDetails().subscribe({next: (t) => this.topics.set(t), error: () => undefined});
    this.search$.pipe(debounceTime(300)).subscribe((value) => {
      this.search.set(value);
      this.goTo(0);
    });
    this.fetch();
  }

  onSearch(value: string): void {
    this.search$.next(value);
  }

  onStatus(value: string): void {
    this.status.set(value as ContentStatus | '');
    this.goTo(0);
  }

  onTopic(value: string): void {
    this.topicId.set(value);
    this.goTo(0);
  }

  goTo(page: number): void {
    this.page.set(page);
    this.fetch();
  }

  topicNamesOf(item: ContentItem): string {
    return item.topicIds.map((id) => this.topicNames().get(id) ?? '—').join(', ');
  }

  open(item: ContentItem): void {
    const ref = this.dialog.open<ContentReviewDialogComponent, ContentItem, ContentItem>(
      ContentReviewDialogComponent,
      {data: item},
    );
    ref.closed.subscribe((updated) => {
      if (updated) {
        this.rows.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
      }
    });
  }

  private fetch(): void {
    this.loading.set(true);
    this.admin
      .searchContents({status: this.status(), topicId: this.topicId() || undefined, search: this.search() || undefined, page: this.page()})
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.totalPages.set(page.totalPages);
          this.totalElements.set(page.totalElements);
          this.first.set(page.first);
          this.last.set(page.last);
          this.loading.set(false);
        },
        error: (e: AppError) => {
          this.loading.set(false);
          this.toast.error(e.message);
        },
      });
  }
}
