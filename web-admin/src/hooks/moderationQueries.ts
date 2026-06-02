import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getModerationSummary,
  warnResident,
  banResident,
  unbanResident,
} from '@/api/moderation'

export function useModerationSummaryQuery(residentId: number | null) {
  return useQuery({
    queryKey: ['moderation', residentId],
    queryFn: () => getModerationSummary(residentId as number),
    enabled: residentId != null,
  })
}

export function useWarnMutation(residentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ reason, complaintId }: { reason: string; complaintId: number }) =>
      warnResident(residentId, reason, complaintId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moderation', residentId] }),
  })
}

export function useBanMutation(residentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ reason }: { reason: string }) => banResident(residentId, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moderation', residentId] }),
  })
}

export function useUnbanMutation(residentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => unbanResident(residentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moderation', residentId] }),
  })
}
