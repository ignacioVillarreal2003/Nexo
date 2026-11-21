import {ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {distinctUntilChanged, map} from 'rxjs';
import {AppError} from '@core/models/api.model';
import {FeedItem, TopicCount} from '@core/models/feed';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {FilterToggleComponent} from '@shared/ui/filter-toggle/filter-toggle.component';
import {
  FiltersDialogComponent,
  FiltersDialogData,
  FiltersDialogResult,
} from '@shared/ui/filters-dialog/filters-dialog.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {clientPagination} from '@shared/utils/client-pagination';
import {pageFromUrl, pageToUrl, syncUrlState} from '@shared/utils/url-state';
import {SkeletonComponent} from '@shared/ui/skeleton/skeleton.component';
import {FeedService} from '../data/feed.service';
import {SubscriptionService} from '../data/subscription.service';
import {FeedActions} from '../ui/feed-actions';
import {FeedItemCardComponent} from '../ui/feed-item-card.component';

const COLLECTING_RETRY_MS = 30_000;
const COLLECTING_MAX_ATTEMPTS = 10;

function fold(text: string | null | undefined): string {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/**
 * "Mi día": lo entregado hoy, con búsqueda y filtro por tópico. Los días anteriores están en Historial.
 * Acepta `?date=` para abrir un día puntual (los avisos de días pasados enlazan así).
 */
@Component({
  selector: 'app-my-day',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent,
    PaginationComponent,
    ButtonComponent,
    EmptyStateComponent,
    FilterToggleComponent,
    IconComponent,
    SkeletonComponent,
    FeedItemCardComponent,
    TranslatePipe,
  ],
  templateUrl: './my-day.component.html',
  styleUrl: './my-day.component.scss',
})
export class MyDayComponent {
  private readonly feed = inject(FeedService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);
  private readonly destroyRef = inject(DestroyRef);
  readonly actions = inject(FeedActions);

  readonly items = signal<FeedItem[]>([]);
  readonly topics = signal<readonly TopicCount[]>([]);
  readonly date = signal('');
  readonly today = signal('');
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly emailing = signal(false);
  readonly query = signal('');
  readonly topicId = signal('');
  readonly filtersOpen = signal(false);
  /** Content for newly followed topics is being collected right now; the page retries on its own. */
  readonly collecting = signal(false);
  private collectingTimer: ReturnType<typeof setTimeout> | null = null;
  private collectingAttempts = 0;
  /** Today's day is prepared on its own at most once per visit, when it opens empty. */
  private autoPrepared = false;

  readonly isToday = computed(() => !this.date() || this.date() === this.today());
  readonly hasSubscriptions = computed(() =>
    this.subscriptions.loaded() ? this.subscriptions.subscriptions().length > 0 : null,
  );
  readonly hasCriteria = computed(() => !!this.query().trim() || !!this.topicId());

  /** The day's items after search and topic filter (both in memory: a day holds a few dozen at most). */
  readonly visible = computed(() => {
    const q = fold(this.query().trim());
    const topic = this.topicId();
    return this.items().filter((i) => (!topic || i.topicId === topic)
      && (!q || fold(`${i.title} ${i.summary} ${i.sourceName ?? ''} ${i.topicName ?? ''}`).includes(q)));
  });

  readonly pager = clientPagination(this.visible);

  /** Búsqueda, filtro y página en la URL (la fecha ya estaba: `?date=`). */
  private readonly urlState = (() => {
    const params = syncUrlState(() => ({
      q: this.query().trim(), topic: this.topicId(), page: pageToUrl(this.pager.page()),
    }));
    this.query.set(params.get('q') ?? '');
    this.topicId.set(params.get('topic') ?? '');
    this.pager.goTo(pageFromUrl(params));
    return params;
  })();

  constructor() {
    this.destroyRef.onDestroy(() => this.clearCollectingTimer());
    this.route.queryParamMap
      .pipe(map((params) => params.get('date') ?? ''), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((date) => this.load(date || undefined));
    if (!this.subscriptions.loaded()) {
      this.subscriptions.load().subscribe({error: () => undefined});
    }
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
                ...this.topics().map((c) => ({value: c.topicId, label: `${c.topicName} (${c.count})`})),
              ],
            },
          ],
          values: {topic: this.topicId()},
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (result) {
        this.topicId.set(result['topic'] ?? '');
        this.pager.reset();
      }
    });
  }

  clearAll(): void {
    this.query.set('');
    this.topicId.set('');
    this.pager.reset();
  }

  onQuery(value: string): void {
    this.query.set(value);
    this.pager.reset();
  }

  goTo(page: number): void {
    this.pager.goTo(page);
  }

  /** "Buscar más": completa el día con lo nuevo que haya; si algún tópico se está recopilando, reintenta solo. */
  refresh(automatic = false): void {
    if (!automatic) {
      this.collectingAttempts = 0;
    }
    this.clearCollectingTimer();
    this.refreshing.set(true);
    this.feed.refresh().subscribe({
      next: ({delivered, collecting}) => {
        this.refreshing.set(false);
        this.collecting.set(collecting && delivered === 0);
        if (collecting && delivered === 0) {
          this.scheduleRetry();
        }
        if (delivered > 0) {
          this.toast.success(this.i18n.translate('feed.myDay.refreshed', {count: delivered}));
          this.load(undefined);
        } else if (!automatic) {
          this.toast.info(this.i18n.translate(collecting ? 'feed.myDay.searching' : 'feed.myDay.nothingNew'));
        }
      },
      error: (e: AppError) => {
        this.refreshing.set(false);
        this.collecting.set(false);
        this.toast.error(e.message);
      },
    });
  }

  /** Manda por mail todas las noticias del día, cada vez que se pide. */
  emailMe(): void {
    this.emailing.set(true);
    this.feed.emailMe().subscribe({
      next: ({emailed, collecting}) => {
        this.emailing.set(false);
        if (emailed > 0) {
          this.toast.success(this.i18n.translate(emailed === 1 ? 'feed.myDay.emailedOne' : 'feed.myDay.emailed',
            {count: emailed}));
          this.load(undefined);
        } else {
          this.toast.info(this.i18n.translate(collecting ? 'feed.myDay.searching' : 'feed.myDay.emailNothing'));
        }
        if (collecting) {
          this.collecting.set(true);
          this.scheduleRetry();
        }
      },
      error: (e: AppError) => {
        this.emailing.set(false);
        this.toast.error(e.message);
      },
    });
  }

  /** While topics are being collected, ask again every 30 s for up to 5 minutes. */
  private scheduleRetry(): void {
    if (this.collectingAttempts >= COLLECTING_MAX_ATTEMPTS) {
      this.collecting.set(false);
      return;
    }
    this.collectingAttempts++;
    this.collectingTimer = setTimeout(() => this.refresh(true), COLLECTING_RETRY_MS);
  }

  private clearCollectingTimer(): void {
    if (this.collectingTimer) {
      clearTimeout(this.collectingTimer);
      this.collectingTimer = null;
    }
  }

  private load(date?: string): void {
    this.loading.set(true);
    this.feed.day(date).subscribe({
      next: (view) => {
        this.date.set(view.date);
        this.today.set(view.today);
        this.items.set([...view.items]);
        this.topics.set(view.topics);
        this.loading.set(false);
        this.autoPrepare(view.items.length);
      },
      error: (e: AppError) => {
        this.loading.set(false);
        this.toast.error(e.message);
      },
    });
  }

  /**
   * Sin botón de "preparar mi día": si hoy está vacío y la persona sigue tópicos (por ejemplo, recién empezó),
   * el día se completa solo. Si algún tópico todavía se está recopilando, refresh() reintenta cada 30 s.
   */
  private autoPrepare(itemCount: number): void {
    if (this.autoPrepared || itemCount > 0 || !this.isToday()) {
      return;
    }
    this.autoPrepared = true;
    const run = () => this.refresh(true);
    if (this.subscriptions.loaded()) {
      if (this.hasSubscriptions()) {
        run();
      }
    } else {
      this.subscriptions.load().subscribe({next: (list) => list.length && run(), error: () => undefined});
    }
  }
}
