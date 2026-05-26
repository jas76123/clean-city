import { useQuery } from '@tanstack/react-query'
import { getTrends, getByDistrict, getByCategory, getSla, getVotesImpact } from '@/api/analytics'
import type { AnalyticsPeriod } from '@/api/types'

// Дашборд автообновляется раз в минуту — синхронно с таблицей жалоб (Day 16).
const DASHBOARD_REFETCH_MS = 60_000

export function useTrendsQuery() {
  return useQuery({
    queryKey: ['analytics', 'trends'],
    queryFn: () => getTrends(),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useByCategoryQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'by-category', period],
    queryFn: () => getByCategory(period),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useByDistrictQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'by-district', period],
    queryFn: () => getByDistrict(period),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useSlaQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'sla', period],
    queryFn: () => getSla(period),
  })
}

export function useVotesImpactQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'votes-impact', period],
    queryFn: () => getVotesImpact(period),
  })
}
