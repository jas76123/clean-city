import { api } from './client'
import type {
  AnalyticsOverview,
  AnalyticsPeriod,
  CategoryStat,
  DistrictStat,
  SlaStat,
  TrendsResponse,
  VotesBucket,
  OperationalSnapshot,
  BurningComplaintItem,
  StrategicKpis,
  ReopenStat,
} from './types'

/** @deprecated Перейти на getOperational + getStrategic. /analytics/overview сохраняется только для legacy ComplaintsPage и будет удалён после полной миграции UI. */
export async function getOverview(): Promise<AnalyticsOverview> {
  const res = await api.get<AnalyticsOverview>('/analytics/overview')
  return res.data
}

export async function getTrends(period?: AnalyticsPeriod, groupBy?: 'day' | 'week' | 'month'): Promise<TrendsResponse> {
  const params: Record<string, string> = {}
  if (period) params.period = period
  if (groupBy) params.groupBy = groupBy
  const res = await api.get<TrendsResponse>('/analytics/trends', Object.keys(params).length > 0 ? { params } : undefined)
  return res.data
}

export async function getByCategory(period: AnalyticsPeriod): Promise<CategoryStat[]> {
  const res = await api.get<CategoryStat[]>('/analytics/by-category', { params: { period } })
  return res.data
}

export async function getByDistrict(period: AnalyticsPeriod): Promise<DistrictStat[]> {
  const res = await api.get<DistrictStat[]>('/analytics/by-district', { params: { period } })
  return res.data
}

export async function getSla(period: AnalyticsPeriod): Promise<SlaStat[]> {
  const res = await api.get<SlaStat[]>('/analytics/sla', { params: { period } })
  return res.data
}

export async function getVotesImpact(period: AnalyticsPeriod): Promise<VotesBucket[]> {
  const res = await api.get<VotesBucket[]>('/analytics/votes-impact', { params: { period } })
  return res.data
}

export async function getOperational(): Promise<OperationalSnapshot> {
  const res = await api.get<OperationalSnapshot>('/analytics/operational')
  return res.data
}

export async function getBurning(limit = 10): Promise<BurningComplaintItem[]> {
  const res = await api.get<BurningComplaintItem[]>('/analytics/burning', { params: { limit } })
  return res.data
}

export async function getStrategic(period: AnalyticsPeriod): Promise<StrategicKpis> {
  const res = await api.get<StrategicKpis>('/analytics/strategic', { params: { period } })
  return res.data
}

export async function getReopen(period: AnalyticsPeriod): Promise<ReopenStat> {
  const res = await api.get<ReopenStat>('/analytics/reopen', { params: { period } })
  return res.data
}
