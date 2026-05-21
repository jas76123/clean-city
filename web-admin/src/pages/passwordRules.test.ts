import { describe, it, expect } from 'vitest'
import { validateAdminPassword } from './passwordRules'

describe('validateAdminPassword', () => {
  it('принимает валидный пароль', () => {
    expect(validateAdminPassword('Secret123!xyz')).toBeNull()
  })
  it('отклоняет короткий пароль', () => {
    expect(validateAdminPassword('Ab1!')).toMatch(/12/)
  })
  it('требует цифру', () => {
    expect(validateAdminPassword('Abcdefgh!xyz')).toMatch(/цифр/i)
  })
  it('требует заглавную букву', () => {
    expect(validateAdminPassword('secret123!xyz')).toMatch(/заглавн/i)
  })
  it('требует спецсимвол', () => {
    expect(validateAdminPassword('Secret123xyzAB')).toMatch(/спецсимвол/i)
  })
})
