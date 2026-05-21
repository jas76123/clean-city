import { api } from './client'
import type { AuthResponse, LoginResponse, MessageResponse } from './types'

export async function login(email: string, password: string): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>('/auth/login', { email, password })
  return res.data
}

export async function loginTwoFactor(challengeToken: string, code: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/auth/login-2fa', { challengeToken, code })
  return res.data
}

export async function acceptInvite(token: string, password: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/auth/admin/accept-invite', { token, password })
  return res.data
}

export async function forgotPassword(email: string): Promise<MessageResponse> {
  const res = await api.post<MessageResponse>('/auth/forgot-password', { email })
  return res.data
}

export async function resetPassword(token: string, newPassword: string): Promise<MessageResponse> {
  const res = await api.post<MessageResponse>('/auth/reset-password', { token, newPassword })
  return res.data
}

export async function logout(refreshToken: string): Promise<void> {
  await api.post('/auth/logout', { refreshToken })
}
