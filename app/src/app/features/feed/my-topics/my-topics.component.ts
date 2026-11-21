import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {AppError} from '@core/models/api.model';
import {Subscription} from '@core/models/feed';
import {Topic, TopicKind} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {FilterToggleComponent} from '@shared/ui/filter-toggle/filter-toggle.component';
import {
  FiltersDialogComponent,
  FiltersDialogData,
  FiltersDialogResult,
} from '@shared/ui/filters-dialog/filters-dialog.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {PaginationComponent} from '@shared/ui/pagination/pagination.component';
import {SkeletonComponent} from '@shared/ui/skeleton/skeleton.component';
import {CatalogService} from '../data/catalog.service';
import {SubscriptionService} from '../data/subscription.service';
import {TopicBadgeComponent} from '../ui/topic-badge.component';
import {clientPagination} from '@shared/utils/client-pagination';
import {pageFromUrl, pageToUrl, syncUrlState} from '@shared/utils/url-state';
import {countryName} from '@shared/utils/country-name';

function fold(text: string | null | undefined): string {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/**
 * Tópicos que sigue el usuario y cuántos contenidos quiere de cada uno por día, con búsqueda y los mismos
 * filtros que Explorar (tipo, país y categoría). La suscripción no trae tipo ni país: salen del catálogo.
 */
@Component({
  selector: 'app-my-topics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    PageHeaderComponent,
    PaginationComponent,
    ButtonComponent,
    EmptyStateComponent,
    FilterToggleComponent,
    IconComponent,
    SkeletonComponent,
    TopicBadgeComponent,
    TranslatePipe,
  ],
  templateUrl: './my-topics.component.html',
  styleUrl: './my-topics.component.scss',
})
export class MyTopicsComponent {
  private readonly subscriptions = inject(SubscriptionService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);
  private readonly catalog = inject(CatalogService);

  readonly items = this.subscriptions.subscriptions;
  readonly loading = signal(true);
  readonly query = signal('');
  readonly category = signal('');
  readonly kind = signal<'' | TopicKind>('');
  readonly country = signal('');
  readonly filtersOpen = signal(false);
  readonly filterCount = computed(() => [this.category(), this.kind(), this.country()].filter(Boolean).length);
  readonly hasCriteria = computed(() => !!this.query().trim() || this.filterCount() > 0);

  /** Tópicos del catálogo por id: de ahí salen el tipo y el país de cada suscripción. */
  private readonly topicsById = signal<ReadonlyMap<string, Topic>>(new Map());

  /** Países de los tópicos que sigo, para el filtro. */
  readonly countries = computed(() => {
    const byId = this.topicsById();
    const codes = new Set(this.items().map((s) => byId.get(s.topicId)?.countryCode).filter(Boolean));
    return [...codes].map((code) => ({code: code as string, name: this.countryLabel(code)}))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  /** Categorías de los tópicos que sigo, para el filtro. */
  readonly categories = computed(() =>
    [...new Set(this.items().map((s) => s.categoryName).filter((c): c is string => !!c))].sort());

  readonly rows = computed(() => {
    const q = fold(this.query().trim());
    const category = this.category();
    const kind = this.kind();
    const country = this.country();
    const byId = this.topicsById();
    return this.items().filter((s) => {
      const topic = byId.get(s.topicId);
      return (!category || s.categoryName === category)
        && (!kind || topic?.kind === kind)
        && (!country || topic?.countryCode === country)
        && (!q || fold(`${s.topicName} ${s.categoryName} ${this.countryLabel(topic?.countryCode)}`).includes(q));
    });
  });
  readonly pager = clientPagination(this.rows);

  /** Búsqueda, filtro y página en la URL. */
  private readonly urlState = (() => {
    const params = syncUrlState(() => ({
      q: this.query().trim(), kind: this.kind(), country: this.country(), category: this.category(),
      page: pageToUrl(this.pager.page()),
    }));
    this.query.set(params.get('q') ?? '');
    this.kind.set((params.get('kind') ?? '') as '' | TopicKind);
    this.country.set(params.get('country') ?? '');
    this.category.set(params.get('category') ?? '');
    this.pager.goTo(pageFromUrl(params));
    return params;
  })();
  readonly optimistic = signal<ReadonlyMap<string, number>>(new Map());
  private readonly pending = new Map<string, ReturnType<typeof setTimeout>>();

  constructor() {
    this.catalog.categories().subscribe({
      next: (categories) => this.topicsById.set(new Map(categories.flatMap((c) => c.topics).map((t) => [t.id, t]))),
      error: () => undefined,
    });
    this.subscriptions.load().subscribe({
      next: () => this.loading.set(false),
      error: (e: AppError) => {
        this.loading.set(false);
        this.toast.error(e.message);
      },
    });
  }

  onQuery(value: string): void {
    this.query.set(value);
    this.pager.reset();
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
              key: 'kind',
              type: 'select',
              label: t('feed.explore.filter.kind'),
              options: [
                {value: '', label: t('feed.explore.filter.all')},
                {value: 'TOPIC', label: t('feed.explore.kind.TOPIC')},
                {value: 'COUNTRY', label: t('feed.explore.kind.COUNTRY')},
                {value: 'OUTLET', label: t('feed.explore.kind.OUTLET')},
              ],
            },
            {
              key: 'country',
              type: 'select',
              label: t('feed.explore.filter.country'),
              options: [
                {value: '', label: t('feed.explore.filter.allCountries')},
                ...this.countries().map((c) => ({value: c.code, label: c.name})),
              ],
            },
            {
              key: 'category',
              type: 'select',
              label: t('feed.explore.col.category'),
              options: [
                {value: '', label: t('feed.explore.allCategories')},
                ...this.categories().map((c) => ({value: c, label: c})),
              ],
            },
          ],
          values: {category: this.category(), kind: this.kind(), country: this.country()},
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (result) {
        this.category.set(result['category'] ?? '');
        this.kind.set((result['kind'] ?? '') as '' | TopicKind);
        this.country.set(result['country'] ?? '');
        this.pager.reset();
      }
    });
  }

  clearAll(): void {
    this.query.set('');
    this.category.set('');
    this.kind.set('');
    this.country.set('');
    this.pager.reset();
  }

  countryLabel(code: string | null | undefined): string {
    return countryName(code, this.i18n.locale());
  }

  /** El cambio se ve al instante y se guarda tras una pausa breve para agrupar varios clics. */
  change(subscription: Subscription, delta: number): void {
    const limit = Math.min(50, Math.max(1, this.limitOf(subscription) + delta));
    if (limit === this.limitOf(subscription)) {
      return;
    }
    clearTimeout(this.pending.get(subscription.topicId));
    this.pending.set(
      subscription.topicId,
      setTimeout(() => {
        this.subscriptions.updateDailyLimit(subscription.topicId, limit).subscribe({
          error: (e: AppError) => this.toast.error(e.message),
        });
      }, 400),
    );
    // Vista optimista mientras se guarda.
    this.optimistic.set(new Map(this.optimistic()).set(subscription.topicId, limit));
  }

  limitOf(subscription: Subscription): number {
    return this.optimistic().get(subscription.topicId) ?? subscription.dailyLimit;
  }

  remove(subscription: Subscription): void {
    this.subscriptions.unsubscribe(subscription.topicId).subscribe({
      next: () =>
        this.toast.info(this.i18n.translate('feed.explore.unfollowed', {name: subscription.topicName ?? ''})),
      error: (e: AppError) => this.toast.error(e.message),
    });
  }
}
