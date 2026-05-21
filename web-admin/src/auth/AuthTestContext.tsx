import { createContext } from 'react'
import type { LoginResponse, UserResponse } from '@/api/types'

export interface AuthContextValue {
  status: 'loading' | 'authenticated' | 'unauthenticated'
  user: UserResponse | null
  login: (email: string, password: string) => Promise<LoginResponse>
  submit2fa: (challengeToken: string, code: string) => Promise<void>
  acceptInvite: (token: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthTestContext = createContext<AuthContextValue | null>(null)
