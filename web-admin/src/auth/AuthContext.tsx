import { useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import * as authApi from '@/api/auth'
import { setSession, clearSession, getRefreshToken, refreshAccessToken } from '@/api/client'
import type { AuthResponse, LoginResponse } from '@/api/types'
import { AuthTestContext, type AuthContextValue } from './AuthTestContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthContextValue['status']>('loading')
  const [user, setUser] = useState<AuthContextValue['user']>(null)
  const queryClient = useQueryClient()

  useEffect(() => {
    let active = true
    if (!getRefreshToken()) {
      setStatus('unauthenticated')
      return
    }
    refreshAccessToken()
      .then((auth) => {
        if (active) applySession(auth)
      })
      .catch(() => {
        if (active) {
          clearSession()
          setStatus('unauthenticated')
        }
      })
    return () => {
      active = false
    }
  }, [])

  function applySession(auth: AuthResponse) {
    setSession(auth)
    setUser(auth.user)
    setStatus('authenticated')
  }

  async function login(email: string, password: string): Promise<LoginResponse> {
    const res = await authApi.login(email, password)
    if (!res.requires2fa && res.auth) {
      applySession(res.auth)
    }
    return res
  }

  async function submit2fa(challengeToken: string, code: string) {
    const auth = await authApi.loginTwoFactor(challengeToken, code)
    applySession(auth)
  }

  async function acceptInvite(token: string, password: string) {
    const auth = await authApi.acceptInvite(token, password)
    applySession(auth)
  }

  async function logout() {
    const rt = getRefreshToken()
    if (rt) {
      try {
        await authApi.logout(rt)
      } catch {
        // best-effort: даже если сервер недоступен — гасим локальную сессию
      }
    }
    clearSession()
    queryClient.clear()
    setUser(null)
    setStatus('unauthenticated')
  }

  return (
    <AuthTestContext.Provider value={{ status, user, login, submit2fa, acceptInvite, logout }}>
      {children}
    </AuthTestContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthTestContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
