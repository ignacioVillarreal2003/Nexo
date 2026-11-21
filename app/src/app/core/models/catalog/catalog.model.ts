export type CatalogStatus = 'ACTIVE' | 'INACTIVE';
export type SourceType = 'RSS' | 'RSS_SEARCH' | 'ARXIV' | 'HACKERNEWS' | 'NEWSAPI';
/** Qué se sigue: un tema, las noticias de un país o todo lo de un diario o sitio. */
export type TopicKind = 'TOPIC' | 'COUNTRY' | 'OUTLET';

export const SOURCE_TYPES: readonly SourceType[] = ['RSS', 'RSS_SEARCH', 'ARXIV', 'HACKERNEWS', 'NEWSAPI'];

export interface Topic {
  readonly id: string;
  readonly categoryId: string;
  readonly categoryName: string;
  readonly slug: string;
  readonly name: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly imageUrl: string | null;
  readonly keywords: readonly string[];
  readonly status: CatalogStatus;
  readonly kind: TopicKind;
  /** ISO 3166-1 alfa-2: el país de un tópico COUNTRY o del medio de un OUTLET. */
  readonly countryCode: string | null;
}

export interface Category {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly imageUrl: string | null;
  readonly sortOrder: number;
  readonly status: CatalogStatus;
  readonly topics: readonly Topic[];
}

/** Vista de administración: el tópico con su configuración de búsqueda y sus fuentes. */
export interface TopicDetail {
  readonly topic: Topic;
  readonly searchQuery: string | null;
  readonly minimumRelevance: number;
  readonly maximumItemsPerRun: number;
  readonly arxivCategories: string | null;
  readonly sourceIds: readonly string[];
}

export interface Source {
  readonly id: string;
  readonly name: string;
  readonly type: SourceType;
  readonly url: string;
  readonly configuration: Record<string, unknown>;
  readonly status: CatalogStatus;
}

export interface CategoryRequest {
  name: string;
  slug?: string | null;
  description?: string | null;
  icon?: string | null;
  imageUrl?: string | null;
  sortOrder: number;
}

export interface TopicRequest {
  categoryId: string;
  name: string;
  slug?: string | null;
  description?: string | null;
  icon?: string | null;
  imageUrl?: string | null;
  keywords: string[];
  searchQuery?: string | null;
  minimumRelevance?: number;
  maximumItemsPerRun?: number;
  arxivCategories?: string | null;
  sourceIds?: string[];
}

export interface SourceRequest {
  name: string;
  type: SourceType;
  url: string;
  configuration: Record<string, unknown>;
}

/** Fuente activa de un tópico, tal como se le muestra al lector. */
export interface TopicSource {
  readonly name: string;
  readonly type: SourceType;
}

export interface ContentPreview {
  readonly id: string;
  readonly title: string;
  readonly summary: string | null;
  readonly sourceName: string | null;
  readonly originalUrl: string;
  readonly imageUrl: string | null;
  readonly publishedAt: string;
}

/** Lo que trae un tópico: publicados en la última semana y lo más reciente. */
export interface TopicContentSummary {
  readonly publishedLast7Days: number;
  readonly latest: readonly ContentPreview[];
}
