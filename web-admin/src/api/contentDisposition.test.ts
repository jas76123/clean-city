import { describe, it, expect } from 'vitest'
import { parseContentDisposition } from './contentDisposition'

describe('parseContentDisposition', () => {
  it('returns filename for attachment with quotes', () => {
    expect(parseContentDisposition('attachment; filename="report.pdf"')).toBe('report.pdf')
  })

  it('returns filename without quotes', () => {
    expect(parseContentDisposition('attachment; filename=report.pdf')).toBe('report.pdf')
  })

  it('returns null when header missing', () => {
    expect(parseContentDisposition(undefined)).toBeNull()
    expect(parseContentDisposition('')).toBeNull()
  })

  it('returns null when filename absent', () => {
    expect(parseContentDisposition('attachment')).toBeNull()
  })
})
