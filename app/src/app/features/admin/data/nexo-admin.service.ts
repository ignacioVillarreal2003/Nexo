import {Injectable, inject} from '@angular/core';
import {Observable} from 'rxjs';
import {PageResponse} from '@core/models/api.model';
import {
  Category,
  CategoryRequest,
  CatalogStatus,
  Source,
  SourceRequest,
  TopicDetail,
  TopicRequest,
} from '@core/models/catalog';
import {
  CollectionState,
  ContentItem,
  ContentReviewRequest,
  ContentStatistics,
  ContentStatus,
  DeliveryRun,
  DeliveryRunResult,
  DeliveryStatistics,
  PipelineError,
  PipelineRun,
  SubscriptionStatistics,
} from '@core/models/pipeline';
import {CollectorApiService} from '@core/services/collector';
import {ContentApiService} from '@core/services/content';
import {DeliveryApiService} from '@core/services/delivery';
import {SubscriptionApiService} from '@core/services/subscription';
import {TopicApiService} from '@core/services/topic';

/** Administración de Nexo: catálogo, contenidos, pipeline y entregas, repartidos en sus servicios. */
@Injectable({providedIn: 'root'})
export class NexoAdminService {
  private readonly topics = inject(TopicApiService);
  private readonly subscriptions = inject(SubscriptionApiService);
  private readonly contents = inject(ContentApiService);
  private readonly deliveries = inject(DeliveryApiService);
  private readonly collector = inject(CollectorApiService);

  // ---------- catálogo ----------
  categories(): Observable<Category[]> {
    return this.topics.get<Category[]>('/api/v1/administration/categories');
  }

  saveCategory(id: string | null, body: CategoryRequest): Observable<Category> {
    return id
      ? this.topics.put<Category>(`/api/v1/administration/categories/${id}`, body)
      : this.topics.post<Category>('/api/v1/administration/categories', body);
  }

  categoryStatus(id: string, status: CatalogStatus): Observable<Category> {
    return this.topics.patch<Category>(`/api/v1/administration/categories/${id}/status`, {status});
  }

  topicDetails(): Observable<TopicDetail[]> {
    return this.topics.get<TopicDetail[]>('/api/v1/administration/topics');
  }

  saveTopic(id: string | null, body: TopicRequest): Observable<TopicDetail> {
    return id
      ? this.topics.put<TopicDetail>(`/api/v1/administration/topics/${id}`, body)
      : this.topics.post<TopicDetail>('/api/v1/administration/topics', body);
  }

  topicStatus(id: string, status: CatalogStatus): Observable<TopicDetail> {
    return this.topics.patch<TopicDetail>(`/api/v1/administration/topics/${id}/status`, {status});
  }

  sources(): Observable<Source[]> {
    return this.topics.get<Source[]>('/api/v1/administration/sources');
  }

  saveSource(id: string | null, body: SourceRequest): Observable<Source> {
    return id
      ? this.topics.put<Source>(`/api/v1/administration/sources/${id}`, body)
      : this.topics.post<Source>('/api/v1/administration/sources', body);
  }

  sourceStatus(id: string, status: CatalogStatus): Observable<Source> {
    return this.topics.patch<Source>(`/api/v1/administration/sources/${id}/status`, {status});
  }

  // ---------- contenidos ----------
  searchContents(query: {status?: ContentStatus | ''; topicId?: string; search?: string; page?: number}):
    Observable<PageResponse<ContentItem>> {
    return this.contents.getPage<ContentItem>('/api/v1/administration/contents', {...query, size: 20});
  }

  reviewContent(id: string, body: ContentReviewRequest): Observable<ContentItem> {
    return this.contents.put<ContentItem>(`/api/v1/administration/contents/${id}`, body);
  }

  contentStatistics(): Observable<ContentStatistics> {
    return this.contents.get<ContentStatistics>('/api/v1/administration/contents/statistics');
  }

  // ---------- pipeline ----------
  runs(page = 0): Observable<PageResponse<PipelineRun>> {
    return this.contents.getPage<PipelineRun>('/api/v1/administration/pipeline-runs', {page, size: 20});
  }

  errors(runId?: string, page = 0): Observable<PageResponse<PipelineError>> {
    return this.contents.getPage<PipelineError>('/api/v1/administration/pipeline-errors', {runId, page, size: 50});
  }

  collect(topicIds?: string[]): Observable<void> {
    return this.collector.post<void>('/api/v1/collections', {topicIds: topicIds ?? null});
  }

  collection(): Observable<CollectionState> {
    return this.collector.get<CollectionState>('/api/v1/collections/current');
  }

  // ---------- entregas ----------
  deliveryRuns(page = 0): Observable<PageResponse<DeliveryRun>> {
    return this.deliveries.getPage<DeliveryRun>('/api/v1/administration/delivery-runs', {page, size: 20});
  }

  deliver(userId?: string): Observable<DeliveryRunResult> {
    return this.deliveries.post<DeliveryRunResult>('/api/v1/administration/delivery-runs', {userId: userId ?? null});
  }

  deliveryStatistics(): Observable<DeliveryStatistics> {
    return this.deliveries.get<DeliveryStatistics>('/api/v1/administration/delivery-statistics');
  }

  subscriptionStatistics(): Observable<SubscriptionStatistics> {
    return this.subscriptions.get<SubscriptionStatistics>('/api/v1/administration/statistics');
  }
}
