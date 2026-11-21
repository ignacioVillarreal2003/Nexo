import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {Router} from '@angular/router';
import {Subject, catchError, debounceTime, of, switchMap} from 'rxjs';
import {CommandPaletteService} from '@core/services/command-palette.service';
import {FeedItem} from '@core/models/feed';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {FeedService} from '@features/feed/data/feed.service';
import {ADMIN_PERMISSIONS} from '@core/models/admin';
import {IconComponent} from '../icon/icon.component';
import {IconName} from '../icon/icons';

interface Command {
  id: string;
  label: string;
  icon: IconName;
  hint?: string;
  run: () => void;
}

/** Atajos de navegación y búsqueda en todo lo que el usuario recibió. */
@Component({
  selector: 'app-command-palette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent {
  private readonly palette = inject(CommandPaletteService);
  private readonly router = inject(Router);
  private readonly feed = inject(FeedService);
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);

  readonly open = this.palette.open;
  readonly query = signal('');
  readonly activeIndex = signal(0);
  readonly hits = signal<FeedItem[]>([]);

  private readonly query$ = new Subject<string>();

  private navCommands(): Command[] {
    const commands: Command[] = [
      {id: 'my-day', label: this.i18n.translate('nav.myDay'), icon: 'sun', run: () => this.go('/')},
      {id: 'explore', label: this.i18n.translate('nav.explore'), icon: 'globe', run: () => this.go('/explore')},
      {id: 'topics', label: this.i18n.translate('nav.myTopics'), icon: 'star', run: () => this.go('/topics')},
      {id: 'favorites', label: this.i18n.translate('nav.favorites'), icon: 'heart', run: () => this.go('/favorites')},
      {id: 'history', label: this.i18n.translate('nav.history'), icon: 'history', run: () => this.go('/history')},
      {
        id: 'notifications',
        label: this.i18n.translate('commandPalette.notifications'),
        icon: 'bell',
        run: () => this.go('/notifications'),
      },
      {
        id: 'settings',
        label: this.i18n.translate('commandPalette.settings'),
        icon: 'settings',
        run: () => this.go('/settings'),
      },
    ];
    const permissions = this.auth.permissions();
    if (ADMIN_PERMISSIONS.some((p) => permissions.has(p))) {
      commands.push({id: 'admin', label: this.i18n.translate('nav.admin'), icon: 'shield', run: () => this.go('/admin')});
    }
    return commands;
  }

  readonly results = computed(() => {
    const q = this.query().trim().toLowerCase();
    const navCommands = this.navCommands();
    const nav = q ? navCommands.filter((c) => c.label.toLowerCase().includes(q)) : navCommands;
    const hitCommands: Command[] = this.hits().map((item) => ({
      id: item.id,
      label: item.title,
      icon: 'file-text',
      hint: item.topicName ?? this.i18n.translate('commandPalette.contentHint'),
      run: () => this.openItem(item),
    }));
    return [...hitCommands, ...nav];
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.query.set('');
        this.hits.set([]);
        this.activeIndex.set(0);
      }
    });

    this.query$
      .pipe(
        debounceTime(200),
        switchMap((q) => {
          if (q.trim().length < 2) {
            return of([] as FeedItem[]);
          }
          return this.feed.search(q.trim()).pipe(catchError(() => of([] as FeedItem[])));
        }),
      )
      .subscribe((hits) => {
        this.hits.set(hits);
        this.activeIndex.set(0);
      });
  }

  onInput(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
    this.query$.next(value);
  }

  onKeydown(event: KeyboardEvent): void {
    const total = this.results().length;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex.set(total ? (this.activeIndex() + 1) % total : 0);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex.set(total ? (this.activeIndex() - 1 + total) % total : 0);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.results()[this.activeIndex()]?.run();
    } else if (event.key === 'Escape') {
      this.close();
    }
  }

  runAt(index: number): void {
    this.results()[index]?.run();
  }

  close(): void {
    this.palette.hide();
  }

  private openItem(item: FeedItem): void {
    this.close();
    window.open(item.originalUrl, '_blank', 'noopener');
  }

  private go(path: string): void {
    this.close();
    this.router.navigateByUrl(path);
  }
}
