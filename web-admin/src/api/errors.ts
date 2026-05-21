import { AxiosError } from 'axios'
import type { ApiError } from './types'

const FALLBACK_MESSAGES: Record<string, string> = {
  ACCOUNT_LOCKED: 'Аккаунт временно заблокирован. Попробуйте позже.',
  RATE_LIMITED: 'Слишком много попыток. Подождите немного.',
  NETWORK: 'Нет связи с сервером. Проверьте подключение.',
  UNKNOWN: 'Произошла ошибка. Попробуйте ещё раз.',
}

/** Приводит любую ошибку axios к единому виду {code, message} для UI. */
export function extractApiError(err: unknown): ApiError {
  if (err instanceof AxiosError) {
    const status = err.response?.status
    const data = err.response?.data as Partial<ApiError> | undefined
    if (data && typeof data.code === 'string' && typeof data.message === 'string') {
      return { code: data.code, message: data.message }
    }
    if (status === 423) return { code: 'ACCOUNT_LOCKED', message: FALLBACK_MESSAGES.ACCOUNT_LOCKED }
    if (status === 429) return { code: 'RATE_LIMITED', message: FALLBACK_MESSAGES.RATE_LIMITED }
    if (!err.response) return { code: 'NETWORK', message: FALLBACK_MESSAGES.NETWORK }
  }
  return { code: 'UNKNOWN', message: FALLBACK_MESSAGES.UNKNOWN }
}
