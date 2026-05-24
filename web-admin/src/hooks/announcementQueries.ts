import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  listAnnouncements,
  createAnnouncement,
  unpublishAnnouncement,
} from '@/api/announcements'
import type { CreateAnnouncementRequest } from '@/api/types'

// Список объявлений автообновляется раз в минуту — как таблица жалоб,
// чтобы админ видел чужие правки без ручного F5.
const ANNOUNCEMENTS_REFETCH_MS = 60_000

export function useAnnouncementsQuery() {
  return useQuery({
    queryKey: ['announcements'],
    queryFn: listAnnouncements,
    placeholderData: keepPreviousData,
    refetchInterval: ANNOUNCEMENTS_REFETCH_MS,
  })
}

export function useCreateAnnouncementMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateAnnouncementRequest) => createAnnouncement(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  })
}

export function useUnpublishAnnouncementMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unpublishAnnouncement(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  })
}
