import { useQuery } from '@tanstack/react-query'
import { getTrends, getByDistrict, getSla, getVotesImpact } from '@/api/analytics'
import { listComplaints } from '@/api/complaints'
import type { AnalyticsPeriod, ComplaintFilter } from '@/api/types'

// Дашборд автообновляется раз в минуту — синхронно с таблицей жалоб (Day 16).
const DASHBOARD_REFETCH_MS = 60_000

export function useTrendsQuery() {
  return useQuery({
    queryKey: ['analytics', 'trends'],
    queryFn: getTrends,
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

const TOP_VOTED_FILTER: ComplaintFilter = {
  status: null, slaBreached: false, category: null, district: null, sort: 'votes', page: 0,
}

export function useTopVotedQuery() {
  return useQuery({
    queryKey: ['analytics', 'top-voted'],
    queryFn: () => listComplaints(TOP_VOTED_FILTER),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useByDistrictQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'by-district', period],
    queryFn: () => getByDistrict(period),
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
