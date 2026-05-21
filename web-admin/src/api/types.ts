export type UserRole = 'RESIDENT' | 'OPERATOR' | 'INSPECTOR' | 'ADMIN'

export interface UserResponse {
  id: number
  email: string
  role: UserRole
  fullName?: string | null
  emailVerified: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  accessExpiresIn: number
  refreshExpiresIn: number
  user: UserResponse
}

export interface LoginResponse {
  requires2fa: boolean
  challengeToken?: string | null
  challengeExpiresIn?: number | null
  auth?: AuthResponse | null
}

export interface MessageResponse {
  message: string
}

export interface ApiError {
  code: string
  message: string
}
