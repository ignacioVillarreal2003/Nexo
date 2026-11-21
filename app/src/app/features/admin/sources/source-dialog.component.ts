import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {toSignal} from '@angular/core/rxjs-interop';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {AppError} from '@core/models/api.model';
import {SOURCE_TYPES, Source, SourceType} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {TextareaComponent} from '@shared/ui/form-field/textarea.component';
import {NexoAdminService} from '../data/nexo-admin.service';

/** Alta o edición de una fuente de información. Cierra con {@code true} si guardó. */
@Component({
  selector: 'app-source-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent, TextFieldComponent, TextareaComponent],
  template: `
    <form class="srd" [formGroup]="form" (ngSubmit)="save()">
      <h2 class="srd__title">{{ i18n.translate(source ? 'admin.sources.edit' : 'admin.sources.new') }}</h2>
      <app-text-field formControlName="name" [label]="i18n.translate('admin.sources.col.name')"/>
      <label class="srd__field">
        <span class="srd__label">{{ i18n.translate('admin.sources.col.type') }}</span>
        <select class="srd__select" formControlName="type">
          @for (t of types; track t) {
            <option [value]="t">{{ t }}</option>
          }
        </select>
        <span class="srd__hint">{{ i18n.translate('admin.sources.typeHelp.' + type()) }}</span>
      </label>
      <app-text-field formControlName="url" [label]="i18n.translate('admin.sources.col.url')"/>
      <app-textarea formControlName="configuration" [rows]="3" [label]="i18n.translate('admin.sources.col.configuration')"
                    [error]="configError() ? i18n.translate('admin.sources.invalidJson') : ''"
                    [invalid]="configError()"/>
      <div class="srd__actions">
        <app-button variant="ghost" type="button" (click)="ref.close()">{{ i18n.translate('common.cancel') }}</app-button>
        <app-button type="submit" [loading]="saving()" [disabled]="form.invalid || configError()">
          {{ i18n.translate('common.save') }}
        </app-button>
      </div>
    </form>
  `,
  styles: [
    `
      .srd {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        width: min(90vw, 560px);
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      .srd__title {
        margin: 0;
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .srd__field {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .srd__label {
        font-size: var(--type-caption-size);
        font-weight: 600;
      }

      .srd__hint {
        font-size: var(--type-caption-size);
        color: var(--text-subtle);
      }

      .srd__select {
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

      .srd__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-xs);
      }
    `,
  ],
})
export class SourceDialogComponent {
  readonly source = inject<Source | null>(DIALOG_DATA);
  readonly ref = inject<DialogRef<boolean>>(DialogRef);
  readonly i18n = inject(TranslationService);
  private readonly admin = inject(NexoAdminService);
  private readonly toast = inject(ToastService);

  readonly types = SOURCE_TYPES;
  readonly saving = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.source?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    type: [(this.source?.type ?? 'RSS') as SourceType, Validators.required],
    url: [this.source?.url ?? '', Validators.required],
    configuration: [JSON.stringify(this.source?.configuration ?? {maxItems: 15})],
  });

  readonly type = toSignal(this.form.controls.type.valueChanges, {initialValue: this.form.controls.type.value});
  private readonly configuration = toSignal(this.form.controls.configuration.valueChanges, {
    initialValue: this.form.controls.configuration.value,
  });
  readonly configError = computed(() => this.parse(this.configuration()) === null);

  save(): void {
    const v = this.form.getRawValue();
    const configuration = this.parse(v.configuration);
    if (configuration === null) {
      return;
    }
    this.saving.set(true);
    this.admin
      .saveSource(this.source?.id ?? null, {name: v.name.trim(), type: v.type, url: v.url.trim(), configuration})
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('admin.sources.saved'));
          this.ref.close(true);
        },
        error: (e: AppError) => {
          this.saving.set(false);
          this.toast.error(e.message);
        },
      });
  }

  private parse(text: string): Record<string, unknown> | null {
    try {
      const value = JSON.parse(text || '{}');
      return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
    } catch {
      return null;
    }
  }
}
