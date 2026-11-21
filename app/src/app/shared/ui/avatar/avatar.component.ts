import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Colores que se pueden elegir para el avatar (la clave es lo que se guarda en la cuenta). */
export const AVATAR_COLORS = {
  blue: '#0066cc',
  violet: '#6d4fd6',
  green: '#16a34a',
  orange: '#ea580c',
  pink: '#db2777',
  teal: '#0d9488',
  red: '#dc2626',
  slate: '#475569',
} as const;

export type AvatarColor = keyof typeof AVATAR_COLORS;
export const AVATAR_COLOR_KEYS = Object.keys(AVATAR_COLORS) as AvatarColor[];

@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="avatar__initials" [style.background]="background()" [attr.aria-label]="name()">{{ initials() }}</span>
  `,
  host: {
    class: 'avatar',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() * 0.4',
  },
  styles: [
    `
      :host {
        display: inline-flex;
        border-radius: var(--radius-pill);
        overflow: hidden;
        flex-shrink: 0;
        user-select: none;
      }
      .avatar__initials {
        width: 100%;
        height: 100%;
        display: grid;
        place-items: center;
        color: #fff;
        font-weight: 600;
        letter-spacing: 0;
      }
    `,
  ],
})
export class AvatarComponent {
  readonly name = input<string>('');
  readonly size = input<number>(32);
  /** Color elegido por la persona; sin elegir, el azul de Nexo. */
  readonly color = input<string | null | undefined>(null);

  readonly background = computed(() => AVATAR_COLORS[this.color() as AvatarColor] ?? AVATAR_COLORS.blue);

  readonly initials = computed(() => {
    const parts = this.name().trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });
}
