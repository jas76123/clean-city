import type { IconStyle } from '@/api/types'

export interface IconStyleMeta {
  /** Подпись кнопки-переключателя типа в форме. */
  label: string
  /** Символ в цветном кружке у элемента списка. */
  glyph: string
  /** Tailwind-классы кружка (фон + текст). */
  className: string
}

export const ICON_STYLE_META: Record<IconStyle, IconStyleMeta> = {
  INFO: { label: 'Инфо', glyph: 'i', className: 'bg-blue-100 text-blue-700' },
  SUCCESS: { label: 'Успех', glyph: '✓', className: 'bg-emerald-100 text-emerald-700' },
  WARNING: { label: 'Предупреждение', glyph: '!', className: 'bg-amber-100 text-amber-800' },
}

export const ICON_STYLE_ORDER: IconStyle[] = ['INFO', 'SUCCESS', 'WARNING']

/** Человекочитаемая строка районов: пустой список или ALL → «Все районы». */
export function formatDistricts(districts: string[]): string {
  if (districts.length === 0 || districts.includes('ALL')) return 'Все районы'
  return districts.join(', ')
}
