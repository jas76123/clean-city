import { api } from './client'
import type { ModerationSummary } from './types'

export async function getModerationSummary(residentId: number): Promise<ModerationSummary> {
  const res = await api.get<ModerationSummary>(`/auth/admin/residents/${residentId}/moderation`)
  return res.data
}

export async function warnResident(
  residentId: number,
  reason: string,
  complaintId: number,
): Promise<void> {
  await api.post(`/auth/admin/residents/${residentId}/warn`, { reason, complaintId })
}

export async function banResident(residentId: number, reason: string): Promise<void> {
  await api.post(`/auth/admin/residents/${residentId}/ban`, { reason })
}

export async function unbanResident(residentId: number): Promise<void> {
  await api.post(`/auth/admin/residents/${residentId}/unban`)
}
