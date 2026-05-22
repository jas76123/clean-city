import { api } from './client'
import type { Announcement, AnnouncementsListResponse, CreateAnnouncementRequest } from './types'

export async function listAnnouncements(): Promise<AnnouncementsListResponse> {
  const res = await api.get<AnnouncementsListResponse>('/announcements', {
    params: { limit: 100 },
  })
  return res.data
}

export async function createAnnouncement(
  req: CreateAnnouncementRequest,
): Promise<Announcement> {
  const res = await api.post<Announcement>('/announcements', req)
  return res.data
}

export async function unpublishAnnouncement(id: number): Promise<void> {
  await api.delete(`/announcements/${id}`)
}
