import {Routes} from '@angular/router';

/**
 * Sección principal de Nexo: un único riel lateral (como admin y ajustes) con Mi día, el catálogo,
 * los tópicos del usuario y su historial.
 */
export const FEED_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/feed-layout.component').then((m) => m.FeedLayoutComponent),
    children: [
      {path: '', pathMatch: 'full', redirectTo: 'my-day'},
      {
        path: 'my-day',
        loadComponent: () => import('./my-day/my-day.component').then((m) => m.MyDayComponent),
      },
      {
        path: 'explore',
        loadComponent: () => import('./explore/explore.component').then((m) => m.ExploreComponent),
      },
      {
        path: 'topics',
        loadComponent: () => import('./my-topics/my-topics.component').then((m) => m.MyTopicsComponent),
      },
      {
        path: 'favorites',
        data: {favorites: true},
        loadComponent: () => import('./history/history.component').then((m) => m.HistoryComponent),
      },
      {
        path: 'history',
        data: {favorites: false},
        loadComponent: () => import('./history/history.component').then((m) => m.HistoryComponent),
      },
    ],
  },
];
