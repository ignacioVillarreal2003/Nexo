import {ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DatePipe} from '@angular/common';
import {Subscription, catchError, forkJoin, interval, of, switchMap, takeWhile} from 'rxjs';
import {AppError} from '@core/models/api.model';
import {
  CollectionState,
  ContentStatistics,
  DeliveryRun,
  DeliveryStatistics,
  PipelineError,
  PipelineRun,
  SubscriptionStatistics,
} from '@core/models/pipeline';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {NexoAdminService} from '../data/nexo-admin.service';
import {statusTone} from '../nexo-ui/status-tone';

/** A run older than this with unprocessed items is treated as stalled, not in progress. */
const PROCESSING_WINDOW_MS = 24 * 60 * 60 * 1000;
const PROCESSING_POLL_MS = 5000;

/** Resumen operativo: estadísticas de uso, procesos diarios, ejecuciones y errores. */
@Component({
  selector: 'app-pipeline-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RelativeTimePipe, PageHeaderComponent, ButtonComponent, CardComponent, BadgeComponent,
    TranslatePipe],
  templateUrl: './pipeline-admin.component.html',
  styleUrls: ['../nexo-ui/admin-table.scss', './pipeline-admin.component.scss'],
})
export class PipelineAdminComponent implements OnInit {
  private readonly admin = inject(NexoAdminService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly subscriptionStats = signal<SubscriptionStatistics | null>(null);
  readonly contentStats = signal<ContentStatistics | null>(null);
  readonly deliveryStats = signal<DeliveryStatistics | null>(null);
  readonly runs = signal<PipelineRun[]>([]);
  readonly errors = signal<PipelineError[]>([]);
  readonly deliveryRuns = signal<DeliveryRun[]>([]);
  readonly collection = signal<CollectionState | null>(null);
  readonly selectedRun = signal<string | null>(null);
  readonly delivering = signal(false);
  readonly tone = statusTone;

  /** Runs from the last day whose documents the AI is still working through. */
  readonly processingRuns = computed(() => this.runs().filter((r) => this.pending(r) > 0
    && Date.now() - new Date(r.startedAt).getTime() < PROCESSING_WINDOW_MS));
  private processingPoll: Subscription | null = null;

  readonly canManagePipeline = this.auth.permissions().has('PIPELINE_MANAGE');
  readonly canDeliver = this.auth.permissions().has('DELIVERY_MANAGE');
  readonly canReadStatistics = this.auth.permissions().has('STATISTICS_READ');

  ngOnInit(): void {
    this.load();
    this.admin.collection().subscribe({next: (state) => this.collection.set(state), error: () => undefined});
  }

  readRate(): string {
    const stats = this.deliveryStats();
    return stats && stats.deliveriesLast7Days
      ? `${Math.round((100 * stats.readLast7Days) / stats.deliveriesLast7Days)}%`
      : '0%';
  }

  /** Items collected by a run that the AI has not finished yet. */
  pending(run: PipelineRun): number {
    return Math.max(0, run.itemsCollected - run.itemsProcessed);
  }

  progress(run: PipelineRun): number {
    return run.itemsCollected ? Math.round((100 * run.itemsProcessed) / run.itemsCollected) : 100;
  }

  statusCount(status: string): number {
    return this.contentStats()?.byStatus[status] ?? 0;
  }

  collect(): void {
    this.admin.collect().subscribe({
      next: () => {
        this.toast.info(this.i18n.translate('admin.pipeline.collectStarted'));
        interval(3000)
          .pipe(
            switchMap(() => this.admin.collection()),
            takeWhile((state) => state.running, true),
            takeUntilDestroyed(this.destroyRef),
          )
          .subscribe((state) => {
            this.collection.set(state);
            if (!state.running) {
              this.toast.success(this.i18n.translate('admin.pipeline.collectFinished', {count: state.itemsPublished}));
              this.load();
            }
          });
      },
      error: (e: AppError) => this.toast.error(e.message),
    });
  }

  deliver(): void {
    this.delivering.set(true);
    this.admin.deliver().subscribe({
      next: (result) => {
        this.delivering.set(false);
        this.toast.success(this.i18n.translate('admin.pipeline.deliveryDone', {
          deliveries: result.deliveries, users: result.users,
        }));
        this.load();
      },
      error: (e: AppError) => {
        this.delivering.set(false);
        this.toast.error(e.message);
      },
    });
  }

  showErrors(runId: string | null): void {
    this.selectedRun.set(runId);
    this.admin.errors(runId ?? undefined).subscribe({next: (page) => this.errors.set(page.content), error: () => undefined});
  }

  duration(start: string, end: string | null): string {
    if (!end) {
      return this.i18n.translate('admin.pipeline.inProgress');
    }
    const seconds = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000);
    return seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
  }

  private load(): void {
    const empty = <T>() => of(null as T | null);
    forkJoin({
      subscriptions: this.canReadStatistics ? this.admin.subscriptionStatistics().pipe(catchError(empty)) : empty(),
      contents: this.canReadStatistics ? this.admin.contentStatistics().pipe(catchError(empty)) : empty(),
      deliveries: this.canReadStatistics ? this.admin.deliveryStatistics().pipe(catchError(empty)) : empty(),
      runs: this.admin.runs().pipe(catchError(empty)),
      deliveryRuns: this.canDeliver ? this.admin.deliveryRuns().pipe(catchError(empty)) : empty(),
    }).subscribe((result) => {
      this.subscriptionStats.set(result.subscriptions as SubscriptionStatistics | null);
      this.contentStats.set(result.contents as ContentStatistics | null);
      this.deliveryStats.set(result.deliveries as DeliveryStatistics | null);
      this.runs.set((result.runs as {content: PipelineRun[]} | null)?.content ?? []);
      this.deliveryRuns.set((result.deliveryRuns as {content: DeliveryRun[]} | null)?.content ?? []);
      this.watchProcessing();
    });
    this.showErrors(this.selectedRun());
  }

  /** Keeps the numbers live while the AI processes; stops by itself once every run is done. */
  private watchProcessing(): void {
    if (this.processingRuns().length === 0 || this.processingPoll) {
      return;
    }
    this.processingPoll = interval(PROCESSING_POLL_MS)
      .pipe(switchMap(() => this.admin.runs().pipe(catchError(() => of(null)))), takeUntilDestroyed(this.destroyRef))
      .subscribe((page) => {
        if (page) {
          this.runs.set(page.content);
        }
        if (this.processingRuns().length === 0) {
          this.processingPoll?.unsubscribe();
          this.processingPoll = null;
          this.load();
        }
      });
  }
}
