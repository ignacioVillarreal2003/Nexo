import {ChangeDetectionStrategy, Component, computed, inject, input, output, signal} from '@angular/core';
import {FeedItem} from '@core/models/feed';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {IconComponent} from '@shared/ui/icon/icon.component';

/** Colores para las etiquetas de fuente: cada medio sale siempre del mismo color. */
const SOURCE_COLORS = ['#2563eb', '#059669', '#c2410c', '#7c3aed', '#db2777', '#0891b2', '#b45309', '#475569', '#16a34a'];

/** Un contenido entregado: título, resumen, fuente, fechas y acciones (leído, favorito). Todas iguales en forma y color. */
@Component({
  selector: 'app-feed-item-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BadgeComponent, IconComponent, RelativeTimePipe, TranslatePipe],
  templateUrl: './feed-item-card.component.html',
  styleUrl: './feed-item-card.component.scss',
})
export class FeedItemCardComponent {
  private readonly i18n = inject(TranslationService);

  readonly item = input.required<FeedItem>();
  readonly showDate = input(false);

  readonly toggleRead = output<FeedItem>();
  readonly toggleFavorite = output<FeedItem>();
  readonly opened = output<FeedItem>();

  readonly imageFailed = signal(false);
  /** Resumen completo desplegado. */
  readonly expanded = signal(false);

  /** Título y resumen en el idioma de la app; si no hay traducción al inglés, la versión principal. */
  readonly title = computed(() => (this.english() && this.item().titleEn) || this.item().title);
  readonly summary = computed(() => (this.english() && this.item().summaryEn) || this.item().summary);
  private readonly english = computed(() => this.i18n.locale().startsWith('en'));

  /** Informe completo en el idioma de la app; campo por campo, el otro idioma si falta la traducción. */
  readonly paragraphs = computed(() => {
    const it = this.item();
    const text = (this.english() && it.detailedSummaryEn) || it.detailedSummary || '';
    return text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
  });
  readonly keyPoints = computed(() => {
    const it = this.item();
    const english = it.keyPointsEn ?? [];
    return (this.english() && english.length ? english : it.keyPoints) ?? [];
  });
  readonly whyItMatters = computed(() => {
    const it = this.item();
    return (this.english() && it.whyItMattersEn) || it.whyItMatters || '';
  });
  readonly hasBrief = computed(() => this.paragraphs().length > 0);

  toggleExpanded(): void {
    const opening = !this.expanded();
    this.expanded.set(opening);
    if (opening) {
      this.opened.emit(this.item());
    }
  }

  /** El medio: "Xataka · vía Google News (ES)" se muestra como "Xataka". */
  readonly source = computed(() => {
    const item = this.item();
    if (item.sourceName) {
      return item.sourceName.split(' · vía ')[0].trim();
    }
    try {
      return new URL(item.originalUrl).hostname.replace(/^www\./, '');
    } catch {
      return '';
    }
  });

  readonly sourceColor = computed(() => {
    let hash = 0;
    for (const ch of this.source()) {
      hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    }
    return SOURCE_COLORS[Math.abs(hash) % SOURCE_COLORS.length];
  });
}
