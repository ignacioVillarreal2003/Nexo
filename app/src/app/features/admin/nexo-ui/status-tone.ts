import {BadgeTone} from '@shared/ui/badge/badge.component';

const TONES: Record<string, BadgeTone> = {
  COMPLETED: 'success',
  PUBLISHED: 'success',
  ACTIVE: 'success',
  RUNNING: 'accent',
  SCHEDULED: 'neutral',
  MANUAL: 'neutral',
  USER: 'neutral',
  PARTIAL: 'warning',
  DUPLICATE: 'warning',
  HIDDEN: 'warning',
  INACTIVE: 'warning',
  FAILED: 'danger',
  REJECTED: 'danger',
};

/** Tono de badge para un estado de ejecución, contenido o catálogo. */
export function statusTone(status: string | null | undefined): BadgeTone {
  return (status && TONES[status]) || 'neutral';
}
