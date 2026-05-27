import { api } from './client'
import type {
  AnalyticsOverview,
  AnalyticsPeriod,
  CategoryStat,
  DistrictStat,
  SlaStat,
  TrendsResponse,
} from './types'

export async function getOverview(): Promise<AnalyticsOverview> {
  const res = await api.get<AnalyticsOverview>('/analytics/overview')
  return res.data
}

export async function getTrends(): Promise<TrendsResponse> {
  const res = await api.get<TrendsResponse>('/analytics/trends')
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
