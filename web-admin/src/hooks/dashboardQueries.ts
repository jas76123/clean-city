import { useQuery } from '@tanstack/react-query'
import {
  getTrends, getByDistrict, getByCategory, getSla, getVotesImpact,
  getOperational, getBurning, getStrategic, getReopen,
} from '@/api/analytics'
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

export function useOperationalQuery() {
  return useQuery({
    queryKey: ['analytics', 'operational'],
    queryFn: getOperational,
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useBurningQuery(limit = 10) {
  return useQuery({
    queryKey: ['analytics', 'burning', limit],
    queryFn: () => getBurning(limit),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useStrategicQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'strategic', period],
    queryFn: () => getStrategic(period),
  })
}

export function useReopenQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'reopen', period],
    queryFn: () => getReopen(period),
  })
}

export function useTrendsRangeQuery(period: AnalyticsPeriod, groupBy: 'day' | 'week' | 'month') {
  return useQuery({
    queryKey: ['analytics', 'trends-range', period, groupBy],
    queryFn: () => getTrends(period, groupBy),
  })
}
