import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError } from 'axios'
import { LoginPage } from './LoginPage'
import { AuthTestContext } from '@/auth/AuthTestContext'
import type { AuthContextValue } from '@/auth/AuthTestContext'

function renderLogin(overrides: Partial<AuthContextValue>) {
  const value: AuthContextValue = {
    status: 'unauthenticated',
    user: null,
    login: async () => ({ requires2fa: false }),
    submit2fa: async () => {},
    acceptInvite: async () => {},
    logout: async () => {},
    ...overrides,
  }
  return render(
    <AuthTestContext.Provider value={value}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthTestContext.Provider>,
  )
}

describe('LoginPage', () => {
  it('при requires2fa показывает шаг ввода кода', async () => {
    const login = vi.fn(async () => ({
      requires2fa: true,
      challengeToken: 'ch-1',
      challengeExpiresIn: 300,
    }))
    renderLogin({ login })
    await userEvent.type(screen.getByLabelText(/почта/i), 'admin@cleancity.local')
    await userEvent.type(screen.getByLabelText(/пароль/i), 'Secret123!xyz')
    await userEvent.click(screen.getByRole('button', { name: /войти/i }))
    expect(login).toHaveBeenCalledWith('admin@cleancity.local', 'Secret123!xyz')
    expect(await screen.findByLabelText(/код/i)).toBeInTheDocument()
  })

  it('показывает ошибку при неверных данных', async () => {
    const login = vi.fn(async () => {
      const err = new AxiosError('invalid')
      err.response = {
        status: 401,
        data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'Неверная пара' },
      } as never
      throw err
    })
    renderLogin({ login })
    await userEvent.type(screen.getByLabelText(/почта/i), 'a@b.c')
    await userEvent.type(screen.getByLabelText(/пароль/i), 'wrongpass1234')
    await userEvent.click(screen.getByRole('button', { name: /войти/i }))
    expect(await screen.findByText(/неверная пара/i)).toBeInTheDocument()
  })
})
