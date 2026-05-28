const LABELS: Record<string, string> = {
  LOGIN_SUCCESS: 'Вход',
  LOGIN_FAIL: 'Неуспешный вход',
  LOGIN_LOCKED: 'Аккаунт заблокирован',
  TWOFA_SETUP_STARTED: 'Начало настройки 2FA',
  TWOFA_ENABLED: '2FA подключено',
  TWOFA_FAIL: 'Неверный 2FA-код',
  TWOFA_LOGIN_SUCCESS: 'Вход с 2FA',
  PASSWORD_RESET: 'Сброс пароля',
  REFRESH_REVOKED: 'Отозван refresh-токен',
  SESSION_REVOKED: 'Завершена сессия',
  ADMIN_INVITE_SENT: 'Отправлено приглашение',
  ADMIN_INVITE_ACCEPTED: 'Приглашение принято',
  ADMIN_INVITE_REVOKED: 'Приглашение отозвано',
  ADMIN_USER_FROZEN: 'Сотрудник заморожен',
  ADMIN_USER_UNFROZEN: 'Сотрудник разморожен',
  COMPLAINT_STATUS_CHANGE: 'Смена статуса жалобы',
  ACCOUNT_DELETED: 'Удаление аккаунта',
}

export function labelForAction(action: string): string {
  return LABELS[action] ?? action
}
