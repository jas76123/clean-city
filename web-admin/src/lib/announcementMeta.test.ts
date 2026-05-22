import { describe, it, expect } from 'vitest'
import { ICON_STYLE_META, ICON_STYLE_ORDER, formatDistricts } from './announcementMeta'

describe('formatDistricts', () => {
  it('пустой список → «Все районы»', () => {
    expect(formatDistricts([])).toBe('Все районы')
  })

  it('список с ALL → «Все районы»', () => {
    expect(formatDistricts(['ALL'])).toBe('Все районы')
  })

  it('конкретные районы → перечисление через запятую', () => {
    expect(formatDistricts(['Центральный', 'Адлерский'])).toBe('Центральный, Адлерский')
  })
})

describe('ICON_STYLE_META', () => {
  it('покрывает три стиля и совпадает с порядком', () => {
    expect(Object.keys(ICON_STYLE_META).sort()).toEqual(['INFO', 'SUCCESS', 'WARNING'])
    expect([...ICON_STYLE_ORDER].sort()).toEqual(['INFO', 'SUCCESS', 'WARNING'])
  })
})
