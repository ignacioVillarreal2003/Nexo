import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {TranslationService} from '@core/services/i18n';
import {IconRailComponent, RailItem} from '@shared/ui/icon-rail/icon-rail.component';

const PIN_STORAGE_KEY = 'nexo.feed.railPinned';

@Component({
  selector: 'app-feed-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, IconRailComponent],
  templateUrl: './feed-layout.component.html',
  styleUrl: './feed-layout.component.scss',
})
export class FeedLayoutComponent {
  private readonly i18n = inject(TranslationService);

  readonly railItems = computed<RailItem[]>(() => {
    const read = this.i18n.translate('feed.nav.group.read');
    const follow = this.i18n.translate('feed.nav.group.follow');
    return [
      {label: this.i18n.translate('nav.myDay'), link: '/my-day', icon: 'sun', group: read},
      {label: this.i18n.translate('nav.favorites'), link: '/favorites', icon: 'star', group: read},
      {label: this.i18n.translate('nav.history'), link: '/history', icon: 'history', group: read},
      {label: this.i18n.translate('nav.explore'), link: '/explore', icon: 'globe', group: follow},
      {label: this.i18n.translate('nav.myTopics'), link: '/topics', icon: 'heart', group: follow},
    ];
  });

  readonly title = computed(() => this.i18n.translate('app.name'));
  readonly expandLabel = computed(() => this.i18n.translate('admin.rail.pin'));
  readonly collapseLabel = computed(() => this.i18n.translate('admin.rail.unpin'));

  readonly railStorageKey = PIN_STORAGE_KEY;
  readonly expanded = signal(localStorage.getItem(PIN_STORAGE_KEY) === 'true');
}
