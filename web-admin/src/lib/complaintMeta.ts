import type { ComplaintStatus, ProblemCategory, ComplaintSort } from '@/api/types'

export interface StatusAction {
  toStatus: ComplaintStatus
  label: string
}

/** Карта допустимых переходов — зеркало backend ALLOWED_TRANSITIONS (SPEC §3.2). */
const TRANSITIONS: Record<ComplaintStatus, StatusAction[]> = {
  NEW: [
    { toStatus: 'IN_PROGRESS', label: 'Принять в работу' },
    { toStatus: 'REJECTED', label: 'Отклонить' },
    { toStatus: 'DUPLICATE', label: 'Дубликат' },
  ],
  IN_PROGRESS: [
    { toStatus: 'RESOLVED', label: 'Решить' },
    { toStatus: 'REJECTED', label: 'Отклонить' },
    { toStatus: 'DUPLICATE', label: 'Дубликат' },
  ],
  RESOLVED: [],
  REJECTED: [],
  DUPLICATE: [],
}

export function allowedActions(status: ComplaintStatus): StatusAction[] {
  return TRANSITIONS[status]
}

export interface StatusMeta {
  label: string
  className: string
}

export const STATUS_META: Record<ComplaintStatus, StatusMeta> = {
  NEW: { label: 'В обработке', className: 'bg-amber-100 text-amber-800' },
  IN_PROGRESS: { label: 'В работе', className: 'bg-blue-100 text-blue-800' },
  RESOLVED: { label: 'Решено', className: 'bg-emerald-100 text-emerald-800' },
  REJECTED: { label: 'Отклонена', className: 'bg-slate-200 text-slate-700' },
  DUPLICATE: { label: 'Дубликат', className: 'bg-slate-200 text-slate-700' },
}

export interface CategoryMeta {
  label: string
  icon: string
}

export const CATEGORY_META: Record<ProblemCategory, CategoryMeta> = {
  GARBAGE: { label: 'Мусор', icon: '🗑' },
  ROADS: { label: 'Дороги', icon: '🛣' },
  SIDEWALKS: { label: 'Тротуары', icon: '🚶' },
  LIGHTING: { label: 'Освещение', icon: '💡' },
  GREENERY: { label: 'Озеленение', icon: '🌳' },
  LANDSCAPING: { label: 'Благоустройство', icon: '🏗' },
  PLAYGROUNDS: { label: 'Площадки', icon: '🛝' },
  PARKS: { label: 'Парки', icon: '🏞' },
  BEACHES: { label: 'Пляжи', icon: '🏖' },
  SAFETY: { label: 'Безопасность', icon: '🚨' },
  VANDALISM: { label: 'Вандализм', icon: '🎨' },
  WATER_SUPPLY: { label: 'Водоснабжение', icon: '🚰' },
  SEWAGE: { label: 'Канализация', icon: '🌊' },
  ELECTRICITY: { label: 'Электроснабжение', icon: '⚡' },
  ECOLOGY: { label: 'Экология', icon: '☣' },
  ACCESSIBILITY: { label: 'Доступная среда', icon: '♿' },
  TRADE: { label: 'Торговля', icon: '🏪' },
  OTHER: { label: 'Прочее', icon: '❓' },
}

export const CATEGORY_ORDER = Object.keys(CATEGORY_META) as ProblemCategory[]

/** 4 каноничных района Сочи — значение = нормализованный label из backend. */
export const DISTRICTS: string[] = ['Центральный', 'Адлерский', 'Хостинский', 'Лазаревский']

/** Маппинг короткого districtCode (из backend) → читаемое русское название. */
export const DISTRICT_CODE_LABEL: Record<string, string> = {
  CEN: 'Центральный',
  ADL: 'Адлерский',
  HOS: 'Хостинский',
  LAZ: 'Лазаревский',
}

export const SORT_OPTIONS: { value: ComplaintSort; label: string }[] = [
  { value: 'date', label: 'По дате' },
  { value: 'priority', label: 'По приоритету' },
  { value: 'votes', label: 'По голосам' },
]
