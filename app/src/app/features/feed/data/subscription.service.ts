import {Injectable, computed, inject, signal} from '@angular/core';
import {Observable, tap} from 'rxjs';
import {DeliverySettings, Subscription} from '@core/models/feed';
import {SubscriptionApiService} from '@core/services/subscription';

/** Tópicos que sigue el usuario y su configuración de entrega. Mantiene en memoria las suscripciones. */
@Injectable({providedIn: 'root'})
export class SubscriptionService {
  private readonly api = inject(SubscriptionApiService);

  private readonly _subscriptions = signal<Subscription[]>([]);
  private readonly _loaded = signal(false);

  readonly subscriptions = this._subscriptions.asReadonly();
  readonly loaded = this._loaded.asReadonly();
  readonly followedTopicIds = computed(() => new Set(this._subscriptions().map((s) => s.topicId)));

  load(): Observable<Subscription[]> {
    return this.api.get<Subscription[]>('/api/v1/subscriptions').pipe(
      tap((list) => {
        this._subscriptions.set(list);
        this._loaded.set(true);
      }),
    );
  }

  subscribe(topicId: string, dailyLimit?: number): Observable<Subscription> {
    return this.api.post<Subscription>('/api/v1/subscriptions', {topicId, dailyLimit}).pipe(
      tap((created) => this._subscriptions.update((list) => [...list.filter((s) => s.topicId !== topicId), created])),
    );
  }

  updateDailyLimit(topicId: string, dailyLimit: number): Observable<Subscription> {
    return this.api.patch<Subscription>(`/api/v1/subscriptions/${topicId}`, {dailyLimit}).pipe(
      tap((updated) => this._subscriptions.update((list) => list.map((s) => (s.topicId === topicId ? updated : s)))),
    );
  }

  unsubscribe(topicId: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/subscriptions/${topicId}`).pipe(
      tap(() => this._subscriptions.update((list) => list.filter((s) => s.topicId !== topicId))),
    );
  }

  settings(): Observable<DeliverySettings> {
    return this.api.get<DeliverySettings>('/api/v1/delivery-settings');
  }

  updateSettings(settings: DeliverySettings): Observable<DeliverySettings> {
    return this.api.put<DeliverySettings>('/api/v1/delivery-settings', settings);
  }
}
