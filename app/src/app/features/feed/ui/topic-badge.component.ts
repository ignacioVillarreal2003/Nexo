import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {topicIcon} from './topic-icon';

/** Ícono propio de una categoría: el dibujo (24×24, trazo en currentColor) y su color. */
interface CategoryGlyph {
  readonly color: string;
  readonly svg: string;
}

/**
 * Íconos dibujados para Nexo, uno por categoría. Los tópicos guardan `cat:<slug de su categoría>`
 * en su campo `icon`, así que se ven igual en Descubrir, en el detalle y en Siguiendo.
 */
const GLYPHS: Record<string, CategoryGlyph> = {
  // Un chip con sus patas.
  tecnologia: {
    color: '#2563eb',
    svg: '<rect x="7" y="7" width="10" height="10" rx="2"/><rect x="10" y="10" width="4" height="4" rx=".5" fill="currentColor" stroke="none"/>'
      + '<path d="M9.5 3v4M14.5 3v4M9.5 17v4M14.5 17v4M3 9.5h4M3 14.5h4M17 9.5h4M17 14.5h4"/>',
  },
  // Un átomo.
  ciencia: {
    color: '#7c3aed',
    svg: '<ellipse cx="12" cy="12" rx="9" ry="3.6"/><ellipse cx="12" cy="12" rx="9" ry="3.6" transform="rotate(60 12 12)"/>'
      + '<ellipse cx="12" cy="12" rx="9" ry="3.6" transform="rotate(120 12 12)"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/>',
  },
  // Una curva que sube sobre un eje.
  'economia-finanzas': {
    color: '#059669',
    svg: '<path d="M4 4v16h16"/><path d="m7 15 4-4 3 3 5-6"/><path d="M15 8h4v4"/>',
  },
  // Un maletín.
  negocios: {
    color: '#b45309',
    svg: '<rect x="3" y="7.5" width="18" height="12" rx="2"/><path d="M9 7.5V6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v1.5"/><path d="M3 13h18"/><path d="M11 13v1.5h2V13"/>',
  },
  // Un libro abierto.
  'cultura-conocimiento': {
    color: '#9333ea',
    svg: '<path d="M12 6.5C10 5 7 4.5 3.5 5v13c3.5-.5 6.5 0 8.5 1.5 2-1.5 5-2 8.5-1.5V5C17 4.5 14 5 12 6.5Z"/><path d="M12 6.5v13"/>',
  },
  // Una pelota.
  deportes: {
    color: '#ea580c',
    svg: '<circle cx="12" cy="12" r="9"/><path d="m12 7.5 3.8 2.8-1.4 4.4H9.6l-1.4-4.4Z" fill="currentColor" fill-opacity=".25"/>'
      + '<path d="M12 7.5V3.2M15.8 10.3l4-1.4M14.4 14.7l2.5 3.6M9.6 14.7l-2.5 3.6M8.2 10.3l-4-1.4"/>',
  },
  // Un edificio con columnas.
  politica: {
    color: '#475569',
    svg: '<path d="M3 9.5 12 4l9 5.5"/><path d="M4.5 9.5h15"/><path d="M6.5 10v7M10 10v7M14 10v7M17.5 10v7"/><path d="M3.5 20h17M5 17.5h14"/>',
  },
  // Un corazón con pulso.
  'salud-bienestar': {
    color: '#e11d48',
    svg: '<path d="M12 20s-7.5-4.4-7.5-10A4.3 4.3 0 0 1 12 7.3 4.3 4.3 0 0 1 19.5 10c0 5.6-7.5 10-7.5 10Z"/><path d="M6.5 12.5h3l1.5-2.5 2 4 1.5-1.5h3"/>',
  },
  // Una hoja.
  'medio-ambiente': {
    color: '#16a34a',
    svg: '<path d="M20 4c-9 0-15 4-15 11a5 5 0 0 0 5 5c7 0 10-6 10-16Z"/><path d="M4 21c3-6 7-9.5 11-11.5"/>',
  },
  // Una claqueta.
  entretenimiento: {
    color: '#db2777',
    svg: '<rect x="3" y="9" width="18" height="11" rx="2"/><path d="m3.5 9 1.5-4.5 15 0L21 9"/><path d="m8 4.5 2 4.5M13 4.5l2 4.5"/><path d="m10.5 12.5 4 2.5-4 2.5Z" fill="currentColor" stroke="none"/>',
  },
  // Una valija.
  'estilo-de-vida': {
    color: '#0891b2',
    svg: '<rect x="5" y="7.5" width="14" height="12" rx="2"/><path d="M9.5 7.5V5.5a1.5 1.5 0 0 1 1.5-1.5h2a1.5 1.5 0 0 1 1.5 1.5v2"/><path d="M9 7.5v12M15 7.5v12"/><path d="M8 21v-1.5M16 21v-1.5"/>',
  },
  // Un globo terráqueo.
  paises: {
    color: '#0d9488',
    svg: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3Z"/>',
  },
  // Un diario.
  medios: {
    color: '#c2410c',
    svg: '<path d="M4 5.5h13v13a2 2 0 0 0 2 2H6a2 2 0 0 1-2-2Z"/><path d="M17 9h3v9.5a2 2 0 0 1-4 0"/>'
      + '<path d="M7 9h7M7 12.5h7M7 16h4"/>',
  },
};

/**
 * Ícono de un tópico en un recuadro del color de su categoría. Si el ícono no es de una categoría conocida
 * (por ejemplo, uno cargado a mano desde la administración), muestra el ícono genérico de la app.
 */
@Component({
  selector: 'app-topic-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="tb" [style.width.px]="size()" [style.height.px]="size()" [style.--tb-color]="glyph()?.color ?? null"
          [class.tb--generic]="!glyph()" aria-hidden="true">
      @if (svg(); as markup) {
        <svg viewBox="0 0 24 24" [attr.width]="size() * 0.55" [attr.height]="size() * 0.55" fill="none"
             stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"
             [innerHTML]="markup"></svg>
      } @else {
        <app-icon [name]="fallback()" [size]="size() * 0.45"/>
      }
    </span>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        flex-shrink: 0;
      }

      .tb {
        display: grid;
        place-items: center;
        border-radius: var(--radius-md);
        color: var(--tb-color);
        background: color-mix(in srgb, var(--tb-color) 13%, var(--surface));
        box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--tb-color) 22%, transparent);
      }

      .tb--generic {
        color: var(--accent);
        background: var(--surface-sunken);
        box-shadow: none;
      }
    `,
  ],
})
export class TopicBadgeComponent {
  private readonly sanitizer = inject(DomSanitizer);

  /** El campo `icon` del tópico o de la categoría (`cat:<slug>` para los íconos propios). */
  readonly icon = input<string | null | undefined>(null);
  readonly size = input(44);

  readonly glyph = computed(() => {
    const icon = this.icon();
    return icon?.startsWith('cat:') ? GLYPHS[icon.slice(4)] ?? null : null;
  });
  // Los dibujos son constantes de este archivo, no datos del usuario: se pueden marcar como seguros.
  readonly svg = computed<SafeHtml | null>(() => {
    const glyph = this.glyph();
    return glyph ? this.sanitizer.bypassSecurityTrustHtml(glyph.svg) : null;
  });
  readonly fallback = computed(() => topicIcon(this.icon()));
}
