import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {Observable, forkJoin} from 'rxjs';
import {AppError} from '@core/models/api.model';
import {Category, Topic, TopicKind} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
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
import {SkeletonComponent} from '@shared/ui/skeleton/skeleton.component';
import {CatalogService} from '../data/catalog.service';
import {SubscriptionService} from '../data/subscription.service';
import {TopicDetailDialogComponent, TopicDetailDialogData} from './topic-detail-dialog.component';
import {TopicBadgeComponent} from '../ui/topic-badge.component';
import {clientPagination} from '@shared/utils/client-pagination';
import {pageFromUrl, pageToUrl, syncUrlState} from '@shared/utils/url-state';
import {countryName} from '@shared/utils/country-name';

function fold(text: string | null | undefined): string {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/** Catálogo de tópicos para seguir o dejar de seguir, como tabla con búsqueda y filtros. */
@Component({
  selector: 'app-explore',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  templateUrl: './explore.component.html',
  styleUrl: './explore.component.scss',
})
export class ExploreComponent {
  private readonly catalog = inject(CatalogService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);

  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly query = signal('');
  readonly busy = signal<ReadonlySet<string>>(new Set());
  readonly followed = this.subscriptions.followedTopicIds;

  readonly categoryId = signal('');
  readonly kind = signal<'' | TopicKind>('');
  readonly country = signal('');
  readonly filtersOpen = signal(false);

  readonly filterCount = computed(() => [this.categoryId(), this.kind(), this.country()]
    .filter(Boolean).length);

  /** Países con algún tópico o medio, para el filtro, ordenados por nombre. */
  readonly countries = computed(() => {
    const codes = new Set(this.categories().flatMap((c) => c.topics.map((t) => t.countryCode)).filter(Boolean));
    return [...codes].map((code) => ({code: code as string, name: this.countryLabel(code)}))
      .sort((a, b) => a.name.localeCompare(b.name));
  });
  readonly hasCriteria = computed(() => !!this.query().trim() || this.filterCount() > 0);

  /** The topics the reader does not follow yet, in catalog order, after search and filters. Following one
   *  takes it off the list (it moves to My topics). */
  readonly rows = computed<Topic[]>(() => {
    const q = fold(this.query().trim());
    const category = this.categoryId();
    const kind = this.kind();
    const country = this.country();
    const followed = this.followed();
    return this.categories()
      .filter((c) => !category || c.id === category)
      .flatMap((c) => c.topics.map((t) => ({...t, categoryName: t.categoryName || c.name})))
      .filter((t) => !kind || t.kind === kind)
      .filter((t) => !country || t.countryCode === country)
      .filter((t) => !q || fold(`${t.name} ${t.description} ${t.categoryName} ${this.countryLabel(t.countryCode)} `
        + t.keywords.join(' ')).includes(q))
      .filter((t) => !followed.has(t.id));
  });

  constructor() {
    forkJoin({catalog: this.catalog.categories(), subscriptions: this.subscriptions.load()}).subscribe({
      next: ({catalog}) => {
        this.categories.set(catalog);
        this.loading.set(false);
      },
      error: (e: AppError) => {
        this.loading.set(false);
        this.toast.error(e.message);
      },
    });
  }

  readonly pager = clientPagination(this.rows, 20);

  /** Búsqueda, filtros y página en la URL. */
  private readonly urlState = (() => {
    const params = syncUrlState(() => ({
      q: this.query().trim(), kind: this.kind(), country: this.country(), category: this.categoryId(),
      page: pageToUrl(this.pager.page()),
    }));
    this.query.set(params.get('q') ?? '');
    this.kind.set((params.get('kind') ?? '') as '' | TopicKind);
    this.country.set(params.get('country') ?? '');
    this.categoryId.set(params.get('category') ?? '');
    this.pager.goTo(pageFromUrl(params));
    return params;
  })();

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
                ...this.categories().map((c) => ({value: c.id, label: c.name})),
              ],
            },
          ],
          values: {category: this.categoryId(), kind: this.kind(), country: this.country()},
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (!result) {
        return;
      }
      this.categoryId.set(result['category'] ?? '');
      this.kind.set((result['kind'] ?? '') as '' | TopicKind);
      this.country.set(result['country'] ?? '');
      this.pager.reset();
    });
  }

  clearAll(): void {
    this.query.set('');
    this.categoryId.set('');
    this.kind.set('');
    this.country.set('');
    this.pager.reset();
  }

  countryLabel(code: string | null | undefined): string {
    return countryName(code, this.i18n.locale());
  }

  openDetail(topic: Topic): void {
    this.dialog.open<TopicDetailDialogComponent, TopicDetailDialogData>(TopicDetailDialogComponent, {data: {topic}});
  }

  toggle(topic: Topic): void {
    const following = this.followed().has(topic.id);
    this.setBusy(topic.id, true);
    const request: Observable<unknown> = following
      ? this.subscriptions.unsubscribe(topic.id)
      : this.subscriptions.subscribe(topic.id);
    request.subscribe({
      next: () => {
        this.setBusy(topic.id, false);
        this.toast.success(
          this.i18n.translate(following ? 'feed.explore.unfollowed' : 'feed.explore.followed', {name: topic.name}),
        );
      },
      error: (e: AppError) => {
        this.setBusy(topic.id, false);
        this.toast.error(e.message);
      },
    });
  }

  private setBusy(id: string, on: boolean): void {
    this.busy.update((set) => {
      const next = new Set(set);
      if (on) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
