export interface NavItem {
  path: string
  label: string
}

export const NAV_ITEMS: NavItem[] = [
  { path: '/overview', label: 'Обзор' },
  { path: '/complaints', label: 'Жалобы' },
  { path: '/announcements', label: 'Объявления' },
  { path: '/analytics', label: 'Аналитика' },
  { path: '/settings', label: 'Настройки' },
]
