import { describe, it, expect } from 'vitest'
import { AxiosError } from 'axios'
import { extractApiError } from './errors'

describe('extractApiError', () => {
  it('достаёт code и message из ответа {code,message}', () => {
    const err = new AxiosError('fail')
    err.response = { status: 401, data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'Bad' } } as never
    const r = extractApiError(err)
    expect(r.code).toBe('AUTH_INVALID_CREDENTIALS')
    expect(r.message).toBe('Bad')
  })

  it('для 423 без тела отдаёт служебный code ACCOUNT_LOCKED', () => {
    const err = new AxiosError('fail')
    err.response = { status: 423, data: {} } as never
    expect(extractApiError(err).code).toBe('ACCOUNT_LOCKED')
  })

  it('для 429 отдаёт code RATE_LIMITED', () => {
    const err = new AxiosError('fail')
    err.response = { status: 429, data: {} } as never
    expect(extractApiError(err).code).toBe('RATE_LIMITED')
  })

  it('для сетевой ошибки отдаёт code NETWORK', () => {
    const err = new AxiosError('Network Error')
    expect(extractApiError(err).code).toBe('NETWORK')
  })
})
