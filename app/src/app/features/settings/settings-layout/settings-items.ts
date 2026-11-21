import {IconName} from '@shared/ui/icon/icons';

export interface SettingsNavItem {
  labelKey: string;
  icon: IconName;
  link: string;
}

export const SETTINGS_ITEMS: SettingsNavItem[] = [
  {labelKey: 'settings.profile.title', icon: 'user', link: 'profile'},
  {labelKey: 'settings.delivery.title', icon: 'clock', link: 'delivery'},
  {labelKey: 'settings.appearance.title', icon: 'sun', link: 'appearance'},
  {labelKey: 'settings.notifications.title', icon: 'bell', link: 'notifications'},
  {labelKey: 'settings.security.title', icon: 'shield', link: 'security'},
  {labelKey: 'settings.sessions.title', icon: 'monitor', link: 'sessions'},
  {labelKey: 'settings.activity.title', icon: 'history', link: 'activity'},
];
