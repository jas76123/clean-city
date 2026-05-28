import { api } from './client'
import type {
  AuditEntryDto,
  AuditLogResponse,
  TeamMemberDto,
  TeamMembersResponse,
} from './types'

export async function listTeamMembers(
  status?: 'active' | 'frozen' | 'pending',
): Promise<TeamMemberDto[]> {
  const params = status ? { status: status.toUpperCase() } : undefined
  const res = await api.get<TeamMembersResponse>('/auth/admin/users', { params })
  return res.data.items
}

export async function inviteTeamMember(
  email: string,
  fullName: string,
  role: 'ADMIN' | 'OPERATOR',
): Promise<void> {
  await api.post('/auth/admin/invite', { email, fullName, role })
}

export async function freezeUser(userId: number): Promise<void> {
  await api.post(`/auth/admin/users/${userId}/freeze`)
}

export async function unfreezeUser(userId: number): Promise<void> {
  await api.post(`/auth/admin/users/${userId}/unfreeze`)
}

export async function revokeInvitation(userId: number): Promise<void> {
  await api.delete(`/auth/admin/invitations/${userId}`)
}

export async function recentAuditEvents(limit = 50): Promise<AuditEntryDto[]> {
  const res = await api.get<AuditLogResponse>('/auth/admin/audit-log', {
    params: { limit },
  })
  return res.data.items
}
