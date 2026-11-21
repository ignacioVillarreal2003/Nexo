import {Signal, computed, signal} from '@angular/core';

/**
 * Paginación en el cliente para listas que ya están completas en memoria (el catálogo, los tópicos que sigo).
 * La página se ajusta sola si la lista se achica (por ejemplo, al dejar de seguir el último de una página).
 */
export function clientPagination<T>(source: Signal<readonly T[]>, pageSize = 10) {
  const requested = signal(0);
  const totalPages = computed(() => Math.max(1, Math.ceil(source().length / pageSize)));
  const page = computed(() => Math.min(requested(), totalPages() - 1));
  const items = computed(() => source().slice(page() * pageSize, (page() + 1) * pageSize));
  return {
    page,
    totalPages,
    items,
    first: computed(() => page() === 0),
    last: computed(() => page() >= totalPages() - 1),
    goTo: (next: number) => requested.set(Math.max(0, next)),
    reset: () => requested.set(0),
  };
}
