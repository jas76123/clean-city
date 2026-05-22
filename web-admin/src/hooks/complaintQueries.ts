import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { listComplaints, getComplaint, changeStatus, findDuplicates } from '@/api/complaints'
import { getOverview } from '@/api/analytics'
import type { ChangeStatusRequest, ComplaintFilter, ProblemCategory } from '@/api/types'

// Таблица жалоб автообновляется раз в минуту, чтобы админ видел новые
// и переведённые жалобы без ручного F5. keepPreviousData ниже гасит мигание.
const COMPLAINTS_REFETCH_MS = 60_000

export function useComplaintsQuery(filter: ComplaintFilter) {
  return useQuery({
    queryKey: ['complaints', filter],
    queryFn: () => listComplaints(filter),
    placeholderData: keepPreviousData,
    refetchInterval: COMPLAINTS_REFETCH_MS,
  })
}

export function useComplaintQuery(id: number | null) {
  return useQuery({
    queryKey: ['complaint', id],
    queryFn: () => getComplaint(id as number),
    enabled: id != null,
  })
}

export function useOverviewQuery() {
  return useQuery({
    queryKey: ['analytics', 'overview'],
    queryFn: getOverview,
    // Счётчики статусов обновляются вместе с таблицей, чтобы не разъезжаться.
    refetchInterval: COMPLAINTS_REFETCH_MS,
  })
}

export function useDuplicatesQuery(
  enabled: boolean,
  lat: number,
  lon: number,
  category: ProblemCategory,
) {
  return useQuery({
    queryKey: ['duplicates', lat, lon, category],
    queryFn: () => findDuplicates(lat, lon, category),
    enabled,
  })
}

export function useChangeStatusMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: ChangeStatusRequest }) => changeStatus(id, req),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['complaints'] })
      qc.invalidateQueries({ queryKey: ['complaint', updated.id] })
      qc.invalidateQueries({ queryKey: ['analytics', 'overview'] })
    },
  })
}
