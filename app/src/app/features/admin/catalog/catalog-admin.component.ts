import {ChangeDetectionStrategy, Component, OnInit, computed, inject, signal} from '@angular/core';
import {forkJoin} from 'rxjs';
import {AppError} from '@core/models/api.model';
import {Category, Source, TopicDetail} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {topicIcon} from '@features/feed/ui/topic-icon';
import {NexoAdminService} from '../data/nexo-admin.service';
import {statusTone} from '../nexo-ui/status-tone';
import {CatalogCategoryDialogComponent, CatalogCategoryDialogData} from './catalog-category-dialog.component';
import {TopicDialogComponent, TopicDialogData} from './topic-dialog.component';

/** Catálogo: categorías y tópicos, con su configuración de búsqueda y sus fuentes. */
@Component({
  selector: 'app-catalog-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, ButtonComponent, CardComponent, BadgeComponent, IconComponent, SpinnerComponent,
    TranslatePipe],
  templateUrl: './catalog-admin.component.html',
  styleUrls: ['../nexo-ui/admin-table.scss', './catalog-admin.component.scss'],
})
export class CatalogAdminComponent implements OnInit {
  private readonly admin = inject(NexoAdminService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly categories = signal<Category[]>([]);
  readonly topics = signal<TopicDetail[]>([]);
  readonly sources = signal<Source[]>([]);
  readonly loading = signal(true);
  readonly tone = statusTone;
  readonly icon = topicIcon;

  private readonly byCategory = computed(() => {
    const map = new Map<string, TopicDetail[]>();
    for (const detail of this.topics()) {
      map.set(detail.topic.categoryId, [...(map.get(detail.topic.categoryId) ?? []), detail]);
    }
    return map;
  });

  ngOnInit(): void {
    this.load();
  }

  topicsOf(categoryId: string): TopicDetail[] {
    return this.byCategory().get(categoryId) ?? [];
  }

  editCategory(category: Category | null): void {
    const ref = this.dialog.open<CatalogCategoryDialogComponent, CatalogCategoryDialogData, boolean>(
      CatalogCategoryDialogComponent,
      {data: {category, nextSortOrder: this.categories().length + 1}},
    );
    ref.closed.subscribe((saved) => saved && this.load());
  }

  editTopic(detail: TopicDetail | null): void {
    const ref = this.dialog.open<TopicDialogComponent, TopicDialogData, boolean>(TopicDialogComponent, {
      data: {detail, categories: this.categories(), sources: this.sources()},
    });
    ref.closed.subscribe((saved) => saved && this.load());
  }

  toggleTopic(detail: TopicDetail): void {
    const status = detail.topic.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.admin.topicStatus(detail.topic.id, status).subscribe({
      next: (updated) => this.topics.update((list) => list.map((t) => (t.topic.id === updated.topic.id ? updated : t))),
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  toggleCategory(category: Category): void {
    const status = category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.admin.categoryStatus(category.id, status).subscribe({
      next: () => {
        this.toast.info(this.i18n.translate('admin.catalog.statusChanged'));
        this.load();
      },
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  private load(): void {
    forkJoin({
      categories: this.admin.categories(),
      topics: this.admin.topicDetails(),
      sources: this.admin.sources(),
    }).subscribe({
      next: ({categories, topics, sources}) => {
        this.categories.set(categories);
        this.topics.set(topics);
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
