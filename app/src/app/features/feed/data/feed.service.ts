import {Injectable, inject} from '@angular/core';
import {Observable, map} from 'rxjs';
import {PageResponse} from '@core/models/api.model';
import {DayView, FeedItem, HistoryQuery} from '@core/models/feed';
import {DeliveryApiService} from '@core/services/delivery';

/** Mi día, historial, favoritos y lecturas (servicio de entregas). */
@Injectable({providedIn: 'root'})
export class FeedService {
  private readonly api = inject(DeliveryApiService);

  day(date?: string, topicId?: string): Observable<DayView> {
    return this.api.get<DayView>('/api/v1/feed', {date, topicId});
  }

  history(query: HistoryQuery): Observable<PageResponse<FeedItem>> {
    return this.api.getPage<FeedItem>('/api/v1/feed/history', {...query});
  }

  /** Búsqueda rápida en todo lo recibido (paleta de comandos). */
  search(term: string): Observable<FeedItem[]> {
    return this.history({search: term, size: 6}).pipe(map((page) => page.content));
  }

  /**
   * Completa la entrega de hoy con los tópicos que todavía no recibieron contenido.
   * `collecting`: algún tópico no tenía contenido y se pidió recopilarlo al momento.
   */
  refresh(): Observable<{delivered: number; collecting: boolean}> {
    return this.api.post<{delivered: number; collecting: boolean}>('/api/v1/feed/refresh');
  }

  /**
   * Manda por correo todas las noticias del día, cada vez que se pide.
   * Antes completa el día con lo nuevo que haya.
   */
  emailMe(): Observable<{emailed: number; collecting: boolean}> {
    return this.api.post<{emailed: number; collecting: boolean}>('/api/v1/feed/email');
  }

  markRead(id: string, read: boolean): Observable<void> {
    return read ? this.api.post<void>(`/api/v1/feed/${id}/read`) : this.api.delete<void>(`/api/v1/feed/${id}/read`);
  }

  setFavorite(id: string, favorite: boolean): Observable<void> {
    return favorite
      ? this.api.post<void>(`/api/v1/feed/${id}/favorite`)
      : this.api.delete<void>(`/api/v1/feed/${id}/favorite`);
  }
}
