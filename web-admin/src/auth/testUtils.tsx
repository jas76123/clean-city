import type { ReactNode } from 'react'
import { AuthTestContext } from './AuthTestContext'
import type { UserResponse } from '@/api/types'

const FAKE_USER: UserResponse = {
  id: 1,
  email: 'admin@cleancity.local',
  role: 'ADMIN',
  fullName: 'Тест Админ',
  emailVerified: true,
  createdAt: '2026-01-01',
}

export function AuthContextTestProvider({
  status,
  user = FAKE_USER,
  children,
}: {
  status: 'loading' | 'authenticated' | 'unauthenticated'
  user?: UserResponse
  children: ReactNode
}) {
  return (
    <AuthTestContext.Provider
      value={{
        status,
        user: status === 'authenticated' ? user : null,
        login: async () => ({ requires2fa: false }),
        submit2fa: async () => {},
        acceptInvite: async () => {},
        logout: async () => {},
      }}
    >
      {children}
    </AuthTestContext.Provider>
  )
}
