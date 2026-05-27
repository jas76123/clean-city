import { describe, it, expect } from 'vitest'
import { ICON_STYLE_META, ICON_STYLE_ORDER } from './announcementMeta'

describe('ICON_STYLE_META', () => {
  it('покрывает три стиля и совпадает с порядком', () => {
    expect(Object.keys(ICON_STYLE_META).sort()).toEqual(['INFO', 'SUCCESS', 'WARNING'])
    expect([...ICON_STYLE_ORDER].sort()).toEqual(['INFO', 'SUCCESS', 'WARNING'])
  })
})
