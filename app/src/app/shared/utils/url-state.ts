import {effect, inject, untracked} from '@angular/core';
import {ActivatedRoute, ParamMap, Router} from '@angular/router';

export type UrlStateValue = string | number | null | undefined;

/**
 * Filtros, búsqueda y página de un listado en la URL (`?q=…&country=UY&page=2`): al recargar o compartir el
 * enlace se vuelve a la misma vista. Devuelve los parámetros con los que se abrió la página, para inicializar
 * el estado; después, cada cambio del estado se escribe en la URL (sin sumar entradas al historial).
 *
 * Los valores vacíos se quitan de la URL. La página se guarda desde 1 (`page=2` es la segunda) y la primera no
 * se escribe.
 */
export function syncUrlState(read: () => Record<string, UrlStateValue>): ParamMap {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const initial = route.snapshot.queryParamMap;

  effect(() => {
    const queryParams: Record<string, string | null> = {};
    for (const [key, value] of Object.entries(read())) {
      queryParams[key] = value === null || value === undefined || value === '' ? null : String(value);
    }
    untracked(() => router.navigate([], {relativeTo: route, queryParams, queryParamsHandling: 'merge', replaceUrl: true}));
  });
  return initial;
}

/** Página 0-based a partir de `?page=` (1-based). */
export function pageFromUrl(params: ParamMap): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page > 1 ? page - 1 : 0;
}

/** Página 0-based → valor de `?page=` (la primera no se escribe). */
export function pageToUrl(page: number): number | null {
  return page > 0 ? page + 1 : null;
}
