import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {AppError} from '@core/models/api.model';
import {Topic, TopicContentSummary, TopicSource} from '@core/models/catalog';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SkeletonComponent} from '@shared/ui/skeleton/skeleton.component';
import {CatalogService} from '../data/catalog.service';
import {SubscriptionService} from '../data/subscription.service';
import {TopicBadgeComponent} from '../ui/topic-badge.component';

export interface TopicDetailDialogData {
  topic: Topic;
}

/** Detalle de un tópico: qué se busca, de dónde sale y una muestra de lo último publicado. */
@Component({
  selector: 'app-topic-detail-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BadgeComponent, ButtonComponent, IconComponent, SkeletonComponent, RelativeTimePipe, TopicBadgeComponent],
  template: `
    <div class="tdd">
      <header class="tdd__head">
        <app-topic-badge [icon]="data.topic.icon" [size]="52"/>
        <div class="tdd__heading">
          <p class="tdd__category">{{ data.topic.categoryName }}</p>
          <h2 class="tdd__title">{{ data.topic.name }}</h2>
        </div>
        <button type="button" class="tdd__close" (click)="ref.close()" [attr.aria-label]="i18n.translate('common.close')">
          <app-icon name="x" [size]="16"/>
        </button>
      </header>

      @if (data.topic.description) {
        <p class="tdd__desc">{{ data.topic.description }}</p>
      }

      <div class="tdd__stats">
        <div class="tdd__stat">
          <span class="tdd__stat-value">{{ summary() ? summary()!.publishedLast7Days : '—' }}</span>
          <span class="tdd__stat-label">{{ i18n.translate('feed.explore.detail.lastWeek') }}</span>
        </div>
        <div class="tdd__stat">
          <span class="tdd__stat-value">{{ sources() ? sources()!.length : '—' }}</span>
          <span class="tdd__stat-label">{{ i18n.translate('feed.explore.detail.sourcesCount') }}</span>
        </div>
        <div class="tdd__stat">
          <span class="tdd__stat-value tdd__stat-value--status" [class.tdd__stat-value--on]="followed().has(data.topic.id)">
            {{ i18n.translate(followed().has(data.topic.id) ? 'feed.explore.following' : 'feed.explore.notFollowing') }}
          </span>
          <span class="tdd__stat-label">{{ i18n.translate('feed.explore.col.status') }}</span>
        </div>
      </div>

      @if (data.topic.keywords.length) {
        <section class="tdd__section">
          <h3 class="tdd__label">{{ i18n.translate('feed.explore.lookingFor') }}</h3>
          <div class="tdd__chips">
            @for (k of data.topic.keywords; track k) {
              <app-badge>{{ k }}</app-badge>
            }
          </div>
        </section>
      }

      @if (sources()?.length) {
        <section class="tdd__section">
          <h3 class="tdd__label">{{ i18n.translate('feed.explore.detail.sources') }}</h3>
          <ul class="tdd__sources">
            @for (s of sources(); track s.name) {
              <li class="tdd__source">
                <span class="tdd__source-name">{{ s.name }}</span>
                <span class="tdd__source-type">{{ i18n.translate('feed.explore.sourceType.' + s.type) }}</span>
              </li>
            }
          </ul>
        </section>
      }

      <section class="tdd__section">
        <h3 class="tdd__label">{{ i18n.translate('feed.explore.detail.latest') }}</h3>
        @if (summary() === null) {
          @for (i of [1, 2]; track i) {
            <app-skeleton height="58px" radius="var(--radius-md)"/>
          }
        } @else if (summary()!.latest.length) {
          <ul class="tdd__latest">
            @for (c of summary()!.latest; track c.id) {
              <li class="tdd__item">
                <a class="tdd__item-title" [href]="c.originalUrl" target="_blank" rel="noopener noreferrer">{{ c.title }}</a>
                <span class="tdd__item-meta">
                  @if (c.sourceName) {
                    {{ c.sourceName }} ·
                  }
                  {{ c.publishedAt | relativeTime }}
                </span>
                @if (c.summary) {
                  <span class="tdd__item-summary">{{ c.summary }}</span>
                }
              </li>
            }
          </ul>
        } @else {
          <p class="tdd__empty">{{ i18n.translate('feed.explore.detail.noContentYet') }}</p>
        }
      </section>

      <div class="tdd__actions">
        <app-button [variant]="followed().has(data.topic.id) ? 'secondary' : 'primary'"
                    [icon]="followed().has(data.topic.id) ? 'check' : 'plus'" [loading]="busy()" (click)="toggle()">
          {{ i18n.translate(followed().has(data.topic.id) ? 'feed.explore.following' : 'feed.explore.follow') }}
        </app-button>
      </div>
    </div>
  `,
  styles: [
    `
      .tdd {
        display: flex;
        flex-direction: column;
        gap: var(--space-md);
        width: min(92vw, 600px);
        max-height: 86vh;
        overflow-y: auto;
        padding: var(--space-lg);
        box-sizing: border-box;
      }

      /* The dialog scrolls as a whole: its blocks keep their height instead of shrinking to fit. */
      .tdd > * {
        flex-shrink: 0;
      }

      .tdd__head {
        display: flex;
        align-items: center;
        gap: var(--space-sm);
      }

      .tdd__heading {
        flex: 1;
        min-width: 0;
      }

      .tdd__category {
        margin: 0;
        font-size: var(--type-fine-size);
        font-weight: 600;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--accent);
      }

      .tdd__title {
        margin: 0;
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .tdd__close {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        border: none;
        border-radius: 50%;
        background: var(--surface-sunken);
        color: var(--text-muted);
        cursor: pointer;
      }

      .tdd__desc {
        margin: 0;
        color: var(--text-muted);
      }

      .tdd__stats {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        overflow: hidden;
      }

      .tdd__stat {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 10px 14px;
        border-left: 1px solid var(--border);
      }

      .tdd__stat:first-child {
        border-left: 0;
      }

      .tdd__stat-value {
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .tdd__stat-value--status {
        font-size: var(--type-body-size);
        line-height: 1.9;
        color: var(--text-subtle);
      }

      .tdd__stat-value--on {
        color: #1f9d55;
      }

      .tdd__stat-label {
        font-size: var(--type-fine-size);
        color: var(--text-subtle);
      }

      .tdd__section {
        display: flex;
        flex-direction: column;
        gap: var(--space-xs);
      }

      .tdd__label {
        margin: 0;
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--text-subtle);
      }

      .tdd__chips {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }

      .tdd__sources,
      .tdd__latest {
        display: flex;
        flex-direction: column;
        margin: 0;
        padding: 0;
        list-style: none;
        border: 1px solid var(--border);
        border-radius: var(--radius-md);
        overflow: hidden;
      }

      .tdd__source,
      .tdd__item {
        border-top: 1px solid var(--border);
      }

      .tdd__source:first-child,
      .tdd__item:first-child {
        border-top: 0;
      }

      .tdd__source {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-sm);
        padding: 8px 14px;
        font-size: var(--type-caption-size);
      }

      .tdd__source-type {
        color: var(--text-subtle);
        white-space: nowrap;
      }

      .tdd__item {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 10px 14px;
      }

      .tdd__item-title {
        font-weight: 600;
        color: var(--text);
        text-decoration: none;
      }

      .tdd__item-title:hover {
        text-decoration: underline;
      }

      .tdd__item-meta {
        font-size: var(--type-fine-size);
        color: var(--text-subtle);
      }

      .tdd__item-summary {
        font-size: var(--type-caption-size);
        color: var(--text-muted);
      }

      .tdd__empty {
        margin: 0;
        font-size: var(--type-caption-size);
        color: var(--text-subtle);
      }

      .tdd__actions {
        display: flex;
        justify-content: flex-end;
      }
    `,
  ],
})
export class TopicDetailDialogComponent {
  readonly data = inject<TopicDetailDialogData>(DIALOG_DATA);
  readonly ref = inject(DialogRef);
  readonly i18n = inject(TranslationService);
  private readonly catalog = inject(CatalogService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly toast = inject(ToastService);

  /** `null` while loading. */
  readonly sources = signal<TopicSource[] | null>(null);
  readonly summary = signal<TopicContentSummary | null>(null);
  readonly busy = signal(false);
  readonly followed = this.subscriptions.followedTopicIds;

  constructor() {
    const id = this.data.topic.id;
    this.catalog.sources(id).subscribe({next: (s) => this.sources.set(s), error: () => this.sources.set([])});
    this.catalog.contentSummary(id).subscribe({
      next: (s) => this.summary.set(s),
      error: () => this.summary.set({publishedLast7Days: 0, latest: []}),
    });
  }

  toggle(): void {
    const topic = this.data.topic;
    const following = this.followed().has(topic.id);
    this.busy.set(true);
    const done = {
      next: () => this.busy.set(false),
      error: (e: AppError) => {
        this.busy.set(false);
        this.toast.error(e.message);
      },
    };
    if (following) {
      this.subscriptions.unsubscribe(topic.id).subscribe(done);
    } else {
      this.subscriptions.subscribe(topic.id).subscribe(done);
    }
  }
}
