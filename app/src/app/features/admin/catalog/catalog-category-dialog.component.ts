import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {AppError} from '@core/models/api.model';
import {Category} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {NexoAdminService} from '../data/nexo-admin.service';

export interface CatalogCategoryDialogData {
  category: Category | null;
  nextSortOrder: number;
}

/** Alta o edición de una categoría del catálogo. Cierra con {@code true} si guardó. */
@Component({
  selector: 'app-catalog-category-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, TextFieldComponent],
  template: `
    <form class="ccd" [formGroup]="form" (ngSubmit)="save()">
      <h2 class="ccd__title">
        {{ i18n.translate(data.category ? 'admin.catalog.editCategory' : 'admin.catalog.newCategory') }}
      </h2>
      <app-text-field formControlName="name" [label]="i18n.translate('admin.catalog.field.name')"/>
      <app-text-field formControlName="description" [label]="i18n.translate('admin.catalog.field.description')"/>
      <div class="ccd__row">
        <app-text-field formControlName="icon" [label]="i18n.translate('admin.catalog.field.icon')"
                        [hint]="i18n.translate('admin.catalog.field.iconHint')"/>
        <app-text-field formControlName="sortOrder" type="number" [label]="i18n.translate('admin.catalog.field.order')"/>
      </div>
      <app-text-field formControlName="slug" [label]="i18n.translate('admin.catalog.field.slug')"
                      [hint]="i18n.translate('admin.catalog.field.slugHint')"/>
      <div class="ccd__actions">
        <app-button variant="ghost" type="button" (click)="ref.close()">{{ i18n.translate('common.cancel') }}</app-button>
        <app-button type="submit" [loading]="saving()" [disabled]="form.invalid">{{ i18n.translate('common.save') }}</app-button>
      </div>
    </form>
  `,
  styles: [
    `
      .ccd {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        width: min(90vw, 480px);
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      .ccd__title {
        margin: 0;
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .ccd__row {
        display: grid;
        grid-template-columns: 1fr 120px;
        gap: var(--space-sm);
      }

      .ccd__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
      }
    `,
  ],
})
export class CatalogCategoryDialogComponent {
  readonly data = inject<CatalogCategoryDialogData>(DIALOG_DATA);
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly i18n = inject(TranslationService);
  private readonly admin = inject(NexoAdminService);
  private readonly toast = inject(ToastService);

  readonly saving = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.data.category?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    description: [this.data.category?.description ?? ''],
    icon: [this.data.category?.icon ?? ''],
    sortOrder: [String(this.data.category?.sortOrder ?? this.data.nextSortOrder)],
    slug: [this.data.category?.slug ?? ''],
  });

  save(): void {
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.admin
      .saveCategory(this.data.category?.id ?? null, {
        name: value.name.trim(),
        description: value.description || null,
        icon: value.icon.trim() || null,
        sortOrder: Number(value.sortOrder) || 0,
        slug: value.slug.trim() || null,
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
}
