import {Injectable, WritableSignal, inject} from '@angular/core';
import {AppError} from '@core/models/api.model';
import {FeedItem} from '@core/models/feed';
import {TranslationService} from '@core/services/i18n';
import {ToastService} from '@core/services/toast.service';
import {FeedService} from '../data/feed.service';

/** Acciones sobre contenidos con actualización optimista: se revierte si el servidor falla. */
@Injectable({providedIn: 'root'})
export class FeedActions {
  private readonly feed = inject(FeedService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  toggleRead(items: WritableSignal<FeedItem[]>, item: FeedItem): void {
    const read = !item.readAt;
    this.patch(items, item.id, {readAt: read ? new Date().toISOString() : null});
    this.feed.markRead(item.id, read).subscribe({
      error: (e: AppError) => {
        this.patch(items, item.id, {readAt: item.readAt});
        this.toast.error(e.message);
      },
    });
  }

  markOpened(items: WritableSignal<FeedItem[]>, item: FeedItem): void {
    if (item.readAt) {
      return;
    }
    this.patch(items, item.id, {readAt: new Date().toISOString()});
    this.feed.markRead(item.id, true).subscribe({error: () => undefined});
  }

  toggleFavorite(items: WritableSignal<FeedItem[]>, item: FeedItem, removeWhenUnfavorited = false): void {
    const favorite = !item.favorite;
    if (!favorite && removeWhenUnfavorited) {
      items.update((list) => list.filter((i) => i.id !== item.id));
    } else {
      this.patch(items, item.id, {favorite});
    }
    this.feed.setFavorite(item.id, favorite).subscribe({
      next: () => this.toast.info(this.i18n.translate(favorite ? 'feed.toast.favorited' : 'feed.toast.unfavorited')),
      error: (e: AppError) => {
        if (!favorite && removeWhenUnfavorited) {
          items.update((list) => [item, ...list]);
        } else {
          this.patch(items, item.id, {favorite: item.favorite});
        }
        this.toast.error(e.message);
      },
    });
  }

  private patch(items: WritableSignal<FeedItem[]>, id: string, changes: Partial<FeedItem>): void {
    items.update((list) => list.map((i) => (i.id === id ? {...i, ...changes} : i)));
  }
}
