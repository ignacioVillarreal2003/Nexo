import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {AppError} from '@core/models/api.model';
import {Category, Source, TopicDetail} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {TextareaComponent} from '@shared/ui/form-field/textarea.component';
import {NexoAdminService} from '../data/nexo-admin.service';

export interface TopicDialogData {
  detail: TopicDetail | null;
  categories: Category[];
  sources: Source[];
}

/** Alta o edición de un tópico: datos, palabras clave, configuración de búsqueda y fuentes. */
@Component({
  selector: 'app-topic-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, BadgeComponent, ButtonComponent, CheckboxComponent, TextFieldComponent, TextareaComponent],
  template: `
    <form class="tpd" [formGroup]="form" (ngSubmit)="save()">
      <h2 class="tpd__title">{{ i18n.translate(data.detail ? 'admin.catalog.editTopic' : 'admin.catalog.newTopic') }}</h2>

      <div class="tpd__grid">
        <app-text-field formControlName="name" [label]="i18n.translate('admin.catalog.field.name')"/>
        <label class="tpd__field">
          <span class="tpd__label">{{ i18n.translate('admin.catalog.field.category') }}</span>
          <select class="tpd__select" formControlName="categoryId">
            @for (c of data.categories; track c.id) {
              <option [value]="c.id">{{ c.name }}</option>
            }
          </select>
        </label>
        <app-text-field formControlName="slug" [label]="i18n.translate('admin.catalog.field.slug')"
                        [hint]="i18n.translate('admin.catalog.field.slugHint')"/>
        <app-text-field formControlName="icon" [label]="i18n.translate('admin.catalog.field.icon')"/>
      </div>
      <app-textarea formControlName="description" [rows]="2" [label]="i18n.translate('admin.catalog.field.description')"/>
      <app-text-field formControlName="keywords" [label]="i18n.translate('admin.catalog.field.keywords')"
                      [hint]="i18n.translate('admin.catalog.field.keywordsHint')"/>
      <app-text-field formControlName="searchQuery" [label]="i18n.translate('admin.catalog.field.searchQuery')"
                      [hint]="i18n.translate('admin.catalog.field.searchQueryHint')"/>
      <div class="tpd__grid tpd__grid--3">
        <app-text-field formControlName="minimumRelevance" type="number"
                        [label]="i18n.translate('admin.catalog.field.minimumRelevance')"/>
        <app-text-field formControlName="maximumItemsPerRun" type="number"
                        [label]="i18n.translate('admin.catalog.field.maximumItems')"/>
        <app-text-field formControlName="arxivCategories" [label]="i18n.translate('admin.catalog.field.arxiv')"/>
      </div>

      <div>
        <span class="tpd__label">{{ i18n.translate('admin.catalog.field.sources') }}</span>
        <div class="tpd__sources">
          @for (s of data.sources; track s.id) {
            <label class="tpd__source">
              <app-checkbox [checked]="selected().has(s.id)" (checkedChange)="toggleSource(s.id)"/>
              <span>{{ s.name }}</span>
              <app-badge>{{ s.type }}</app-badge>
              @if (s.status !== 'ACTIVE') {
                <app-badge tone="warning">{{ i18n.translate('admin.catalog.status.INACTIVE') }}</app-badge>
              }
            </label>
          }
        </div>
      </div>

      <div class="tpd__actions">
        <app-button variant="ghost" type="button" (click)="ref.close()">{{ i18n.translate('common.cancel') }}</app-button>
        <app-button type="submit" [loading]="saving()" [disabled]="form.invalid">{{ i18n.translate('common.save') }}</app-button>
      </div>
    </form>
  `,
  styles: [
    `
      .tpd {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        width: min(94vw, 760px);
        max-height: 88vh;
        overflow-y: auto;
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      .tpd__title {
        margin: 0;
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .tpd__grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: var(--space-sm);
      }

      .tpd__field {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .tpd__label {
        display: block;
        font-size: var(--type-caption-size);
        font-weight: 600;
        margin-bottom: 6px;
      }

      .tpd__select {
        height: 44px;
        padding: 0 12px;
        border: none;
        border-radius: var(--radius-md);
        background: var(--surface);
        box-shadow: inset 0 0 0 1px var(--border);
        font-family: var(--font-text);
        font-size: var(--type-body-size);
        color: var(--text);
      }

      .tpd__sources {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
        gap: 6px;
      }

      .tpd__source {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: var(--type-caption-size);
        cursor: pointer;
      }

      .tpd__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
      }
    `,
  ],
})
export class TopicDialogComponent {
  readonly data = inject<TopicDialogData>(DIALOG_DATA);
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly i18n = inject(TranslationService);
  private readonly admin = inject(NexoAdminService);
  private readonly toast = inject(ToastService);

  readonly saving = signal(false);
  readonly selected = signal<ReadonlySet<string>>(new Set(this.initialSources()));

  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.data.detail?.topic.name ?? '', [Validators.required, Validators.maxLength(120)]],
    categoryId: [this.data.detail?.topic.categoryId ?? this.data.categories[0]?.id ?? '', Validators.required],
    slug: [this.data.detail?.topic.slug ?? ''],
    icon: [this.data.detail?.topic.icon ?? ''],
    description: [this.data.detail?.topic.description ?? ''],
    keywords: [this.data.detail?.topic.keywords.join(', ') ?? ''],
    searchQuery: [this.data.detail?.searchQuery ?? ''],
    minimumRelevance: [String(this.data.detail?.minimumRelevance ?? 0.55)],
    maximumItemsPerRun: [String(this.data.detail?.maximumItemsPerRun ?? 25)],
    arxivCategories: [this.data.detail?.arxivCategories ?? ''],
  });

  toggleSource(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  save(): void {
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.admin
      .saveTopic(this.data.detail?.topic.id ?? null, {
        categoryId: v.categoryId,
        name: v.name.trim(),
        slug: v.slug.trim() || null,
        icon: v.icon.trim() || null,
        description: v.description || null,
        keywords: v.keywords.split(',').map((k) => k.trim()).filter(Boolean),
        searchQuery: v.searchQuery.trim() || null,
        minimumRelevance: Number(v.minimumRelevance),
        maximumItemsPerRun: Number(v.maximumItemsPerRun),
        arxivCategories: v.arxivCategories.trim() || null,
        sourceIds: [...this.selected()],
      })
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('admin.catalog.saved'));
          this.ref.close(true);
        },
        error: (e: AppError) => {
          this.saving.set(false);
          this.toast.error(e.message);
        },
      });
  }

  private initialSources(): string[] {
    if (this.data.detail) {
      return [...this.data.detail.sourceIds];
    }
    // Un tópico nuevo arranca con la búsqueda de Google News en español.
    return this.data.sources.filter((s) => s.name === 'Google News (ES)').map((s) => s.id);
  }
}
