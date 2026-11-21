import {IconName} from '@shared/ui/icon/icons';

/**
 * Los tópicos guardan un nombre de icono libre (el catálogo lo edita la administración); se traduce
 * a uno del set de la app y, si no existe, se usa uno genérico.
 */
const ALIASES: Record<string, IconName> = {
  memory: 'database',
  smart_toy: 'brain',
  code: 'code',
  security: 'shield',
  cloud: 'globe',
  developer_board: 'database',
  precision_manufacturing: 'settings',
  insights: 'target',
  science: 'sparkles',
  bolt: 'sparkles',
  rocket_launch: 'plane',
  biotech: 'heart',
  medical_services: 'heart',
  psychology: 'brain',
  trending_up: 'wallet',
  public: 'globe',
  candlestick_chart: 'wallet',
  show_chart: 'wallet',
  currency_bitcoin: 'wallet',
  savings: 'wallet',
  business_center: 'folder',
  lightbulb: 'sparkles',
  rocket: 'plane',
  auto_awesome: 'sparkles',
  campaign: 'message-circle',
  groups: 'users',
  menu_book: 'book',
  account_balance: 'archive',
  self_improvement: 'brain',
  favorite: 'heart',
  map: 'globe',
  auto_stories: 'book',
  sports_soccer: 'target',
  newspaper: 'file-text',
  flag: 'globe',
};

export function topicIcon(name: string | null | undefined): IconName {
  if (!name) {
    return 'star';
  }
  return ALIASES[name] ?? (name as IconName);
}
