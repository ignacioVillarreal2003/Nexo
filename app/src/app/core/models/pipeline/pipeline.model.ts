export type ContentStatus = 'PUBLISHED' | 'DUPLICATE' | 'REJECTED' | 'HIDDEN';
export type ContentRelation = 'NEW' | 'UPDATE' | 'DUPLICATE';
export type RunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';

export interface ContentItem {
  readonly id: string;
  readonly originalUrl: string;
  readonly sourceName: string | null;
  readonly originalTitle: string;
  readonly title: string;
  readonly summary: string;
  readonly bodyExcerpt: string | null;
  readonly imageUrl: string | null;
  readonly author: string | null;
  readonly language: string | null;
  readonly keywords: readonly string[];
  readonly entities: readonly string[];
  readonly publicationDate: string | null;
  readonly collectedAt: string;
  readonly qualityScore: number | null;
  readonly status: ContentStatus;
  readonly relation: ContentRelation;
  readonly relatedContentId: string | null;
  readonly similarity: number | null;
  readonly rejectionReason: string | null;
  readonly aiMode: string | null;
  readonly editedByAdministrator: boolean;
  readonly topicIds: readonly string[];
}

export interface ContentReviewRequest {
  title?: string;
  summary?: string;
  status?: ContentStatus;
}

export interface ContentStatistics {
  readonly byStatus: Record<string, number>;
  readonly publishedLast24Hours: number;
  readonly publishedLast7Days: number;
}

export interface PipelineRun {
  readonly id: string;
  readonly trigger: 'SCHEDULED' | 'MANUAL';
  readonly status: RunStatus;
  readonly startedAt: string;
  readonly finishedAt: string | null;
  readonly topicsPlanned: number;
  readonly itemsCollected: number;
  readonly itemsProcessed: number;
  readonly itemsPublished: number;
  readonly itemsDuplicate: number;
  readonly itemsRejected: number;
  readonly itemsFailed: number;
  readonly notes: string | null;
}

export interface PipelineError {
  readonly id: string;
  readonly runId: string | null;
  readonly stage: string;
  readonly source: string | null;
  readonly topicId: string | null;
  readonly message: string;
  readonly createdAt: string;
}

export interface DeliveryRun {
  readonly id: string;
  readonly trigger: 'SCHEDULED' | 'MANUAL' | 'USER';
  readonly status: RunStatus;
  readonly startedAt: string;
  readonly finishedAt: string | null;
  readonly usersProcessed: number;
  readonly deliveriesCreated: number;
  readonly errors: number;
  readonly notes: string | null;
}

export interface DeliveryRunResult {
  readonly runId: string;
  readonly users: number;
  readonly deliveries: number;
  readonly errors: number;
}

export interface DeliveryStatistics {
  readonly deliveriesLast24Hours: number;
  readonly deliveriesLast7Days: number;
  readonly readLast7Days: number;
  readonly favorites: number;
  readonly usersServedLast7Days: number;
}

export interface SubscriptionStatistics {
  readonly activeSubscriptions: number;
  readonly subscribedUsers: number;
  readonly topTopics: readonly {topicId: string; topicName: string | null; subscribers: number}[];
}

export interface CollectionState {
  readonly running: boolean;
  readonly runId: string | null;
  readonly trigger: string;
  readonly status: string;
  readonly startedAt: string | null;
  readonly finishedAt: string | null;
  readonly topicsPlanned: number;
  readonly topicsDone: number;
  readonly itemsPublished: number;
  readonly sourceErrors: number;
}
