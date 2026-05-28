import type { ReactNode } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Toaster } from 'sonner'
import { AxiosError, AxiosHeaders } from 'axios'
import * as adminApi from '@/api/admin'
import { SettingsPage } from './SettingsPage'
import { useAuth } from '@/auth/AuthContext'
import type { UserResponse } from '@/api/types'

vi.mock('@/api/admin')
vi.mock('@/auth/AuthContext', () => ({
  useAuth: vi.fn(),
}))

function wrap(ui: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <Toaster />
      {ui}
    </QueryClientProvider>,
  )
}

const fakeMember = {
  id: 1,
  email: 'a@b.c',
  fullName: 'Иван',
  role: 'OPERATOR' as const,
  district: null,
  status: 'ACTIVE' as const,
  createdAt: '2026-05-28T00:00:00Z',
  lastLoginAt: '2026-05-28T01:00:00Z',
  invitedAt: null,
}

function fakeUser(role: UserResponse['role']): UserResponse {
  return {
    id: 99,
    email: 'me@cleancity.dev',
    role,
    fullName: 'Я',
    emailVerified: true,
    createdAt: '2026-05-01T00:00:00Z',
  }
}

function mockAuthRole(role: UserResponse['role']) {
  vi.mocked(useAuth).mockReturnValue({
    status: 'authenticated',
    user: fakeUser(role),
    login: vi.fn(),
    submit2fa: vi.fn(),
    acceptInvite: vi.fn(),
    logout: vi.fn(),
  })
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.mocked(adminApi.listTeamMembers).mockResolvedValue([fakeMember])
    vi.mocked(adminApi.recentAuditEvents).mockResolvedValue([])
  })

  it('renders team and audit sections', () => {
    mockAuthRole('ADMIN')
    wrap(<SettingsPage />)
    expect(screen.getByText('Команда')).toBeInTheDocument()
    expect(screen.getByText('Журнал событий')).toBeInTheDocument()
  })

  it('admin sees invite and action buttons', async () => {
    mockAuthRole('ADMIN')
    wrap(<SettingsPage />)
    expect(screen.getByText('Пригласить сотрудника')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Заморозить' })).toBeInTheDocument()
    })
  })

  it('operator does not see action buttons', async () => {
    mockAuthRole('OPERATOR')
    wrap(<SettingsPage />)
    expect(screen.queryByText('Пригласить сотрудника')).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('a@b.c')).toBeInTheDocument()
    })
    expect(screen.queryByRole('button', { name: 'Заморозить' })).not.toBeInTheDocument()
  })

  it('freeze action shows confirm dialog with revoke warning', async () => {
    mockAuthRole('ADMIN')
    wrap(<SettingsPage />)
    await waitFor(() => screen.getByRole('button', { name: 'Заморозить' }))
    await userEvent.click(screen.getByRole('button', { name: 'Заморозить' }))
    expect(screen.getByText(/все активные сессии будут отозваны/i)).toBeInTheDocument()
  })

  it('freeze success calls api and closes dialog', async () => {
    mockAuthRole('ADMIN')
    vi.mocked(adminApi.freezeUser).mockResolvedValue(undefined)
    wrap(<SettingsPage />)
    await waitFor(() => screen.getByRole('button', { name: 'Заморозить' }))
    await userEvent.click(screen.getByRole('button', { name: 'Заморозить' }))
    // в диалоге появляется вторая кнопка "Заморозить" — кликаем по последней (внутри dialog)
    const confirmButtons = screen.getAllByRole('button', { name: 'Заморозить' })
    await userEvent.click(confirmButtons[confirmButtons.length - 1])
    await waitFor(() => {
      expect(adminApi.freezeUser).toHaveBeenCalledWith(1)
    })
  })

  it('last active admin error shows friendly toast', async () => {
    mockAuthRole('ADMIN')
    const apiErr = new AxiosError(
      'Conflict',
      'ERR_BAD_REQUEST',
      undefined,
      undefined,
      {
        status: 409,
        statusText: 'Conflict',
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: { code: 'LAST_ACTIVE_ADMIN', message: 'Это последний активный администратор' },
      },
    )
    vi.mocked(adminApi.freezeUser).mockRejectedValue(apiErr)
    wrap(<SettingsPage />)
    await waitFor(() => screen.getByRole('button', { name: 'Заморозить' }))
    await userEvent.click(screen.getByRole('button', { name: 'Заморозить' }))
    const confirmButtons = screen.getAllByRole('button', { name: 'Заморозить' })
    await userEvent.click(confirmButtons[confirmButtons.length - 1])
    await waitFor(() => {
      expect(screen.getByText(/последний активный администратор/i)).toBeInTheDocument()
    })
  })

  it('tab counts update', async () => {
    mockAuthRole('ADMIN')
    vi.mocked(adminApi.listTeamMembers).mockImplementation(async (s) => {
      if (s === 'active') return [fakeMember]
      if (s === 'frozen') return []
      if (s === 'pending') return []
      return []
    })
    wrap(<SettingsPage />)
    await waitFor(() => {
      expect(screen.getByText(/Активные \(1\)/)).toBeInTheDocument()
      expect(screen.getByText(/Замороженные \(0\)/)).toBeInTheDocument()
      expect(screen.getByText(/Ожидают \(0\)/)).toBeInTheDocument()
    })
  })

  it('pending tab shows revoke button only', async () => {
    mockAuthRole('ADMIN')
    const pending = {
      ...fakeMember,
      id: 9,
      status: 'PENDING' as const,
      lastLoginAt: null,
      invitedAt: '2026-05-28T00:00:00Z',
    }
    vi.mocked(adminApi.listTeamMembers).mockImplementation(async (s) => {
      if (s === 'pending') return [pending]
      return []
    })
    wrap(<SettingsPage />)
    await userEvent.click(screen.getByText(/Ожидают/))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Отозвать' })).toBeInTheDocument()
    })
    expect(screen.queryByRole('button', { name: 'Заморозить' })).not.toBeInTheDocument()
  })
})
