import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {Subject, debounceTime, map} from 'rxjs';
import {AppError} from '@core/models/api.model';
import {FeedItem} from '@core/models/feed';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {FilterToggleComponent} from '@shared/ui/filter-toggle/filter-toggle.component';
import {
  FiltersDialogComponent,
  FiltersDialogData,
  FiltersDialogResult,
} from '@shared/ui/filters-dialog/filters-dialog.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {pageFromUrl, pageToUrl, syncUrlState} from '@shared/utils/url-state';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {SkeletonComponent} from '@shared/ui/skeleton/skeleton.component';
import {FeedService} from '../data/feed.service';
import {SubscriptionService} from '../data/subscription.service';
import {FeedActions} from '../ui/feed-actions';
import {FeedItemCardComponent} from '../ui/feed-item-card.component';

const PAGE_SIZE = 10;

/** Todo lo recibido, se haya leído o no. En /favorites muestra solo lo guardado. */
@Component({
  selector: 'app-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent,
    ButtonComponent,
    EmptyStateComponent,
    IconComponent,
    FilterToggleComponent,
    PaginationComponent,
    SkeletonComponent,
    FeedItemCardComponent,
    TranslatePipe,
  ],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
})
export class HistoryComponent {
  private readonly feed = inject(FeedService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(DialogService);
  private readonly i18n = inject(TranslationService);
  readonly actions = inject(FeedActions);

  readonly favorites = toSignal(this.route.data.pipe(map((d) => !!d['favorites'])), {initialValue: false});
  readonly topics = this.subscriptions.subscriptions;
  readonly items = signal<FeedItem[]>([]);
  readonly search = signal('');
  readonly topicId = signal('');
  /** Rango de días de entrega (YYYY-MM-DD), ambos incluidos. */
  readonly from = signal('');
  readonly to = signal('');
  readonly readState = signal<'' | 'unread' | 'read'>('');
  readonly filterCount = computed(() => [this.topicId(), this.from(), this.to(), this.readState()].filter(Boolean).length);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly last = signal(true);
  readonly filtersOpen = signal(false);

  readonly filtered = computed(() => !!this.search() || this.filterCount() > 0);
  private readonly search$ = new Subject<string>();

  /** Búsqueda, filtros y página en la URL; se leen antes de la primera carga. */
  private readonly urlState = (() => {
    const params = syncUrlState(() => ({
      q: this.search().trim(), topic: this.topicId(), from: this.from(), to: this.to(), state: this.readState(),
      page: pageToUrl(this.page()),
    }));
    this.search.set(params.get('q') ?? '');
    this.topicId.set(params.get('topic') ?? '');
    this.from.set(params.get('from') ?? '');
    this.to.set(params.get('to') ?? '');
    this.readState.set((['unread', 'read'].includes(params.get('state') ?? '') ? params.get('state') : '') as '' | 'unread' | 'read');
    this.page.set(pageFromUrl(params));
    return params;
  })();

  constructor() {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) => {
      this.search.set(value);
      this.reload();
    });
    this.route.data.pipe(takeUntilDestroyed()).subscribe(() => this.fetch());
    if (!this.subscriptions.loaded()) {
      this.subscriptions.load().subscribe({error: () => undefined});
    }
  }

  onSearch(value: string): void {
    this.search$.next(value);
  }

  openFilters(): void {
    this.filtersOpen.set(true);
    const t = (key: string) => this.i18n.translate(key);
    const ref = this.dialog.open<FiltersDialogComponent, FiltersDialogData, FiltersDialogResult | undefined>(
      FiltersDialogComponent,
      {
        data: {
          title: t('feed.explore.filters'),
          fields: [
            {
              key: 'topic',
              type: 'select',
              label: t('feed.explore.col.topic'),
              options: [
                {value: '', label: t('feed.history.allTopics')},
                ...[...this.topics()]
                  .sort((a, b) => (a.topicName ?? '').localeCompare(b.topicName ?? ''))
                  .map((s) => ({value: s.topicId, label: s.topicName ?? ''})),
              ],
            },
            {
              key: 'state',
              type: 'select',
              label: t('feed.history.state'),
              options: [
                {value: '', label: t('feed.history.state.all')},
                {value: 'unread', label: t('feed.history.state.unread')},
                {value: 'read', label: t('feed.history.state.read')},
              ],
            },
          ],
          dateRange: {
            fromKey: 'from',
            toKey: 'to',
            fromLabel: t('feed.history.from'),
            toLabel: t('feed.history.to'),
          },
          values: {topic: this.topicId(), state: this.readState(), from: this.from(), to: this.to()},
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (!result) {
        return;
      }
      let from = result['from'] ?? '';
      let to = result['to'] ?? '';
      if (from && to && from > to) {
        [from, to] = [to, from]; // un rango al revés se entiende igual
      }
      this.topicId.set(result['topic'] ?? '');
      this.readState.set((result['state'] ?? '') as '' | 'unread' | 'read');
      this.from.set(from);
      this.to.set(to);
      this.reload();
    });
  }

  clearAll(): void {
    this.search.set('');
    this.topicId.set('');
    this.from.set('');
    this.to.set('');
    this.readState.set('');
    this.reload();
  }

  goTo(page: number): void {
    this.page.set(page);
    this.fetch();
  }

  private reload(): void {
    this.page.set(0);
    this.items.set([]);
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    this.feed
      .history({
        favorites: this.favorites(),
        search: this.search() || undefined,
        topicId: this.topicId() || undefined,
        from: this.from() || undefined,
        to: this.to() || undefined,
        read: this.readState() ? this.readState() === 'read' : undefined,
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (page) => {
          this.items.set(page.content);
          this.totalPages.set(page.totalPages);
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
