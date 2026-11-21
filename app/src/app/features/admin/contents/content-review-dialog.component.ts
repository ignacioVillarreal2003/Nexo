import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {FormBuilder, ReactiveFormsModule} from '@angular/forms';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {AppError} from '@core/models/api.model';
import {ContentItem, ContentStatus} from '@core/models/pipeline';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {TextareaComponent} from '@shared/ui/form-field/textarea.component';
import {NexoAdminService} from '../data/nexo-admin.service';
import {statusTone} from '../nexo-ui/status-tone';

/** Corrección manual de un contenido: título, resumen y estado (publicar, ocultar, rechazar). */
@Component({
  selector: 'app-content-review-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, BadgeComponent, ButtonComponent, TextFieldComponent, TextareaComponent],
  template: `
    <form class="crd" [formGroup]="form" (ngSubmit)="save(null)">
      <h2 class="crd__title">{{ i18n.translate('admin.contents.review') }}</h2>

      <div class="crd__meta">
        <app-badge [tone]="tone(item.status)">{{ i18n.translate('admin.contents.statusValue.' + item.status) }}</app-badge>
        @if (item.similarity !== null) {
          <app-badge>{{ i18n.translate('admin.contents.similarity') }} {{ item.similarity | number: '1.2-2' }}</app-badge>
        }
        @if (item.editedByAdministrator) {
          <app-badge tone="accent">{{ i18n.translate('admin.contents.edited') }}</app-badge>
        }
        <span class="crd__spacer"></span>
        <a [href]="item.originalUrl" target="_blank" rel="noopener" class="crd__link">
          {{ i18n.translate('admin.contents.openOriginal') }}
        </a>
      </div>
      <p class="crd__original">{{ i18n.translate('admin.contents.originalTitle') }}: {{ item.originalTitle }}</p>

      <app-text-field formControlName="title" [label]="i18n.translate('admin.contents.fieldTitle')"/>
      <app-textarea formControlName="summary" [rows]="5" [label]="i18n.translate('admin.contents.fieldSummary')"/>

      @if (item.keywords.length) {
        <div class="crd__chips">
          @for (k of item.keywords; track k) {
            <app-badge>{{ k }}</app-badge>
          }
        </div>
      }
      @if (item.bodyExcerpt) {
        <details class="crd__excerpt">
          <summary>{{ i18n.translate('admin.contents.excerpt') }}</summary>
          <p>{{ item.bodyExcerpt }}</p>
        </details>
      }

      <div class="crd__actions">
        @if (item.status !== 'HIDDEN') {
          <app-button variant="danger" type="button" [disabled]="saving()" (click)="save('HIDDEN')">
            {{ i18n.translate('admin.contents.hide') }}
          </app-button>
        }
        <span class="crd__spacer"></span>
        <app-button variant="ghost" type="button" (click)="ref.close()">{{ i18n.translate('common.cancel') }}</app-button>
        <app-button variant="secondary" type="submit" [disabled]="saving()">{{ i18n.translate('common.save') }}</app-button>
        @if (item.status !== 'PUBLISHED') {
          <app-button type="button" [disabled]="saving()" (click)="save('PUBLISHED')">
            {{ i18n.translate('admin.contents.publish') }}
          </app-button>
        }
      </div>
    </form>
  `,
  styles: [
    `
      .crd {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        width: min(92vw, 680px);
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      .crd__title {
        margin: 0;
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .crd__meta,
      .crd__actions,
      .crd__chips {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 6px;
      }

      .crd__spacer {
        flex: 1;
      }

      .crd__link {
        color: var(--accent);
        font-size: var(--type-caption-size);
      }

      .crd__original {
        margin: 0;
        color: var(--text-subtle);
        font-size: var(--type-caption-size);
      }

      .crd__excerpt p {
        white-space: pre-wrap;
        max-height: 240px;
        overflow: auto;
        color: var(--text-muted);
        font-size: var(--type-caption-size);
      }
    `,
  ],
})
export class ContentReviewDialogComponent {
  readonly item = inject<ContentItem>(DIALOG_DATA);
  readonly ref = inject<DialogRef<ContentItem>>(DialogRef);
  readonly i18n = inject(TranslationService);
  private readonly admin = inject(NexoAdminService);
  private readonly toast = inject(ToastService);

  readonly saving = signal(false);
  readonly tone = statusTone;
  readonly form = inject(FormBuilder).nonNullable.group({
    title: [this.item.title],
    summary: [this.item.summary],
  });

  save(status: ContentStatus | null): void {
    this.saving.set(true);
    const {title, summary} = this.form.getRawValue();
    this.admin.reviewContent(this.item.id, {title, summary, status: status ?? undefined}).subscribe({
      next: (updated) => {
        this.toast.success(this.i18n.translate('admin.contents.saved'));
        this.ref.close(updated);
      },
      error: (e: AppError) => {
        this.saving.set(false);
        this.toast.error(e.message);
      },
    });
  }
}
