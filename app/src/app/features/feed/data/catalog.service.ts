import {Injectable, inject} from '@angular/core';
import {Observable} from 'rxjs';
import {Category, TopicContentSummary, TopicSource} from '@core/models/catalog';
import {ContentApiService} from '@core/services/content';
import {TopicApiService} from '@core/services/topic';

/** Catálogo público de categorías y tópicos, y lo que trae cada tópico. */
@Injectable({providedIn: 'root'})
export class CatalogService {
  private readonly api = inject(TopicApiService);
  private readonly content = inject(ContentApiService);

  categories(): Observable<Category[]> {
    return this.api.get<Category[]>('/api/v1/categories');
  }

  /** Fuentes activas de las que se recopila el tópico. */
  sources(topicId: string): Observable<TopicSource[]> {
    return this.api.get<TopicSource[]>(`/api/v1/topics/${topicId}/sources`);
  }

  /** Cuánto se publicó en la última semana y lo más reciente. */
  contentSummary(topicId: string, limit = 3): Observable<TopicContentSummary> {
    return this.content.get<TopicContentSummary>(`/api/v1/topics/${topicId}/content-summary`, {limit});
  }
}
