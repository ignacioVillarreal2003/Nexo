import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-filter-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button
      type="button"
      class="ft"
      (click)="toggle.emit()"
      [attr.aria-label]="ariaLabel()"
      [attr.aria-expanded]="open()"
    >
      <app-icon name="filter" [size]="18" />
    </button>
  `,
  styles: [
    `
      :host {
        display: flex;
        align-self: center;
      }
      .ft {
        display: grid;
        place-items: center;
        width: 44px;
        height: 44px;
        flex-shrink: 0;
        border-radius: var(--radius-md);
        border: none;
        background: var(--surface-sunken);
        color: var(--text);
        cursor: pointer;
        transition: background var(--duration-fast) var(--ease-standard);

        &:hover {
          background: var(--surface-raised);
        }
      }
    `,
  ],
})
export class FilterToggleComponent {
  readonly open = input(false);
  readonly ariaLabel = input('');
  readonly toggle = output<void>();
}
