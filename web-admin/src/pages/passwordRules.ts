/** Правила пароля админа (зеркало backend AuthService.validatePassword). */
export function validateAdminPassword(pw: string): string | null {
  if (pw.length < 12) return 'Пароль должен быть не короче 12 символов'
  if (!/[0-9]/.test(pw)) return 'Пароль должен содержать цифру'
  if (!/[A-ZА-Я]/.test(pw)) return 'Пароль должен содержать заглавную букву'
  if (!/[^A-Za-zА-Яа-я0-9]/.test(pw)) return 'Пароль должен содержать спецсимвол'
  return null
}
