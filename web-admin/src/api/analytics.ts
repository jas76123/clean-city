import { api } from './client'
import type { AnalyticsOverview } from './types'

export async function getOverview(): Promise<AnalyticsOverview> {
  const res = await api.get<AnalyticsOverview>('/analytics/overview')
  return res.data
}
