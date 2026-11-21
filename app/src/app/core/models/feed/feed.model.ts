export type DeliveryRelation = 'NEW' | 'UPDATE';

export interface Subscription {
  readonly id: string;
  readonly topicId: string;
  readonly topicName: string | null;
  readonly topicSlug: string | null;
  readonly topicIcon: string | null;
  readonly categoryName: string | null;
  readonly dailyLimit: number;
  readonly subscribedAt: string;
}

export interface DeliverySettings {
  readonly timezone: string;
  readonly deliveryHour: number;
  readonly maximumDailyItems: number;
  readonly paused: boolean;
}

export interface FeedItem {
  readonly id: string;
  readonly contentId: string;
  readonly topicId: string;
  readonly topicName: string | null;
  readonly deliveryDate: string;
  readonly deliveredAt: string;
  readonly readAt: string | null;
  readonly favorite: boolean;
  readonly title: string;
  readonly summary: string;
  /** La misma noticia en inglés; null si no hay traducción. */
  readonly titleEn: string | null;
  readonly summaryEn: string | null;
  /** Informe completo (lo escribe la IA leyendo la nota entera): resumen en párrafos, puntos clave, por qué importa. */
  readonly detailedSummary: string | null;
  readonly detailedSummaryEn: string | null;
  readonly keyPoints: string[] | null;
  readonly keyPointsEn: string[] | null;
  readonly whyItMatters: string | null;
  readonly whyItMattersEn: string | null;
  /** Minutos de lectura de la nota original. */
  readonly readingMinutes: number | null;
  readonly originalUrl: string;
  readonly sourceName: string | null;
  readonly imageUrl: string | null;
  readonly publicationDate: string | null;
  readonly collectedAt: string | null;
  readonly relation: DeliveryRelation;
}

export interface TopicCount {
  readonly topicId: string;
  readonly topicName: string | null;
  readonly count: number;
}

export interface DayView {
  readonly date: string;
  readonly today: string;
  readonly topics: readonly TopicCount[];
  readonly items: readonly FeedItem[];
}

export interface HistoryQuery {
  topicId?: string;
  /** Rango de días de entrega (YYYY-MM-DD), ambos incluidos. */
  from?: string;
  to?: string;
  /** `true` solo leídas, `false` solo sin leer. */
  read?: boolean;
  search?: string;
  favorites?: boolean;
  page?: number;
  size?: number;
}
