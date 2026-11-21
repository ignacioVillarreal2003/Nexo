import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import {
  AppNotification,
  NotificationCategory,
  NotificationQuery,
  NotificationSeverity,
  NOTIFICATION_SEVERITIES,
} from '@core/models/notifications';
import { AppError } from '@core/models/api.model';
import { ToastService } from '@core/services/toast.service';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { NotificationService } from './data/notification.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { BadgeComponent, BadgeTone } from '@shared/ui/badge/badge.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { FilterToggleComponent } from '@shared/ui/filter-toggle/filter-toggle.component';
import { SelectOption } from '@shared/ui/form-field/select-field.component';
import {
  FiltersDialogComponent,
  FiltersDialogData,
  FiltersDialogResult,
} from '@shared/ui/filters-dialog/filters-dialog.component';
import { SkeletonComponent } from '@shared/ui/skeleton/skeleton.component';
import { pageFromUrl, pageToUrl, syncUrlState } from '@shared/utils/url-state';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

const PAGE_SIZE = 20;

const SEVERITY_TONES: Record<NotificationSeverity, BadgeTone> = {
  INFO: 'neutral',
  SUCCESS: 'success',
  WARNING: 'warning',
  ERROR: 'danger',
};

const FILTERABLE_CATEGORIES: NotificationCategory[] = [
  'DELIVERY_DAILY_DIGEST',
  'DELIVERY_ON_DEMAND',
  'AUTHENTICATION_SECURITY',
  'SYSTEM',
];

@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    IconComponent,
    BadgeComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    PaginationComponent,
    FilterToggleComponent,
    SkeletonComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements OnInit {
  private readonly service = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly i18n = inject(TranslationService);

  readonly items = signal<AppNotification[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  readonly unread = this.service.unreadCount;

  readonly filtersOpen = signal(false);
  readonly unreadOnly = signal(false);
  readonly search = new FormControl<string>('', { nonNullable: true });
  readonly category = new FormControl<string>('', { nonNullable: true });
  readonly severity = new FormControl<string>('', { nonNullable: true });
  // Copias como signals de los controles, para escribirlas en la URL.
  private readonly searchTerm = signal('');
  private readonly categoryValue = signal('');
  private readonly severityValue = signal('');

  readonly busyId = signal<string | null>(null);

  readonly categoryOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.i18n.translate('notifications.filter.allCategories') },
    ...FILTERABLE_CATEGORIES.map((code) => ({
      value: code,
      label: this.i18n.translate(`settings.notifications.categories.${code}`),
    })),
  ]);

  readonly severityOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.i18n.translate('notifications.filter.allSeverities') },
    ...NOTIFICATION_SEVERITIES.map((value) => ({
      value,
      label: this.i18n.translate(`notifications.severity.${value}`),
    })),
  ]);

  readonly hasFilters = computed(
    () => this.unreadOnly() || !!this.search.value || !!this.category.value || !!this.severity.value,
  );

  /** Búsqueda, filtros y página en la URL, como en el resto de los listados. */
  private readonly urlState = (() => {
    const params = syncUrlState(() => ({
      q: this.searchTerm().trim(),
      state: this.unreadOnly() ? 'unread' : '',
      category: this.categoryValue(),
      severity: this.severityValue(),
      page: pageToUrl(this.page()),
    }));
    this.search.setValue(params.get('q') ?? '', { emitEvent: false });
    this.searchTerm.set(this.search.value);
    this.unreadOnly.set(params.get('state') === 'unread');
    this.category.setValue(params.get('category') ?? '', { emitEvent: false });
    this.categoryValue.set(this.category.value);
    this.severity.setValue(params.get('severity') ?? '', { emitEvent: false });
    this.severityValue.set(this.severity.value);
    this.page.set(pageFromUrl(params));
    return params;
  })();

  constructor() {
    this.search.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => this.searchTerm.set(value));
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.reload());

    this.category.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reload());
    this.severity.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.reload());

    // Live arrivals land at the top of the inbox without a refresh.
    this.service.incoming$.pipe(takeUntilDestroyed()).subscribe((notification) => {
      this.prepend(notification);
    });
  }

  ngOnInit(): void {
    this.load();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  /** Estado (todas / sin leer), categoría e importancia, en el mismo diálogo de filtros que el resto. */
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
              key: 'state',
              type: 'select',
              label: t('notifications.filter.state'),
              options: [
                { value: '', label: t('notifications.filter.allStates') },
                { value: 'unread', label: t('notifications.filter.unreadOnly') },
              ],
            },
            { key: 'category', type: 'select', label: t('notifications.filter.category'), options: this.categoryOptions() },
            { key: 'severity', type: 'select', label: t('notifications.filter.severity'), options: this.severityOptions() },
          ],
          values: {
            state: this.unreadOnly() ? 'unread' : '',
            category: this.category.value,
            severity: this.severity.value,
          },
        },
      },
    );
    ref.closed.subscribe((result) => {
      this.filtersOpen.set(false);
      if (!result) {
        return;
      }
      this.unreadOnly.set(result['state'] === 'unread');
      this.category.setValue(result['category'] ?? '', { emitEvent: false });
      this.severity.setValue(result['severity'] ?? '', { emitEvent: false });
      this.categoryValue.set(this.category.value);
      this.severityValue.set(this.severity.value);
      this.reload();
    });
  }

  clearFilters(): void {
    this.unreadOnly.set(false);
    this.search.setValue('', { emitEvent: false });
    this.category.setValue('', { emitEvent: false });
    this.severity.setValue('', { emitEvent: false });
    this.searchTerm.set('');
    this.categoryValue.set('');
    this.severityValue.set('');
    this.reload();
  }

  toneFor(severity: NotificationSeverity): BadgeTone {
    return SEVERITY_TONES[severity] ?? 'neutral';
  }

  categoryLabel(category: NotificationCategory): string {
    const label = this.i18n.translate(`settings.notifications.categories.${category}`);
    return label.startsWith('settings.notifications.categories.') ? category : label;
  }

  open(item: AppNotification): void {
    this.markRead(item);
    const link = this.linkFor(item);
    if (link) {
      this.router.navigateByUrl(link);
    }
  }

  markRead(item: AppNotification): void {
    if (item.read) {
      return;
    }
    this.patch(item.id, { read: true });
    this.service.markRead(item.id).subscribe({
      error: (err: AppError) => {
        this.patch(item.id, { read: false });
        this.toast.error(err.message);
      },
    });
  }

  async remove(item: AppNotification): Promise<void> {
    const confirmed = await this.dialog.confirm({
      title: this.i18n.translate('notifications.delete.title'),
      message: this.i18n.translate('notifications.delete.message'),
      confirmLabel: this.i18n.translate('common.delete'),
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.busyId.set(item.id);
    this.service.remove(item.id, !item.read).subscribe({
      next: () => {
        this.busyId.set(null);
        this.items.update((list) => list.filter((n) => n.id !== item.id));
        this.totalElements.update((total) => Math.max(0, total - 1));
        this.toast.success(this.i18n.translate('notifications.toast.deleted'));
        if (this.items().length === 0 && this.totalElements() > 0) {
          this.load();
        }
      },
      error: (err: AppError) => {
        this.busyId.set(null);
        this.toast.error(err.message);
      },
    });
  }

  markAll(): void {
    this.service.markAllRead().subscribe({
      next: () => {
        if (this.unreadOnly()) {
          this.reload();
          return;
        }
        this.items.update((list) => list.map((n) => ({ ...n, read: true })));
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  private reload(): void {
    this.page.set(0);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.list(this.page(), PAGE_SIZE, this.query()).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: (err: AppError) => {
        this.loading.set(false);
        this.toast.error(err.message);
      },
    });
  }

  private query(): NotificationQuery {
    return {
      unreadOnly: this.unreadOnly(),
      search: this.search.value.trim() || undefined,
      category: this.category.value || undefined,
      severity: this.severity.value || undefined,
    };
  }

  /** Inserts a streamed notification, as long as the current view would have listed it. */
  private prepend(notification: AppNotification): void {
    if (this.page() !== 0 || !this.matchesFilters(notification)) {
      return;
    }
    this.items.update((list) => [notification, ...list.filter((n) => n.id !== notification.id)].slice(0, PAGE_SIZE));
    this.totalElements.update((total) => total + 1);
  }

  private matchesFilters(notification: AppNotification): boolean {
    const term = this.search.value.trim().toLowerCase();
    if (this.category.value && notification.category !== this.category.value) {
      return false;
    }
    if (this.severity.value && notification.severity !== this.severity.value) {
      return false;
    }
    if (this.unreadOnly() && notification.read) {
      return false;
    }
    if (term) {
      const haystack = `${notification.title} ${notification.body ?? ''}`.toLowerCase();
      return haystack.includes(term);
    }
    return true;
  }

  private patch(id: string, changes: Partial<AppNotification>): void {
    this.items.update((list) => list.map((n) => (n.id === id ? { ...n, ...changes } : n)));
  }

  /** Los avisos del resumen diario traen la ruta de Mi día para esa fecha en {@code data.link}. */
  private linkFor(item: AppNotification): string | null {
    const link = item.data?.['link'];
    return link && link.startsWith('/') ? link : null;
  }
}
