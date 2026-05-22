import { useState } from 'react'
import type { AnalyticsPeriod } from '@/api/types'
import { useOverviewQuery } from '@/hooks/complaintQueries'
import { useTrendsQuery, useSlaQuery, useVotesImpactQuery } from '@/hooks/dashboardQueries'
import { PeriodSwitcher } from './analytics/PeriodSwitcher'
import { TrendCard } from './analytics/TrendCard'
import { SlaByCategory } from './analytics/SlaByCategory'
import { VotesImpactCard } from './analytics/VotesImpactCard'

export function AnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>('MONTH')
  const overview = useOverviewQuery()
  const trends = useTrendsQuery()
  const sla = useSlaQuery(period)
  const votes = useVotesImpactQuery(period)

  if (overview.isError) {
    return (
      <div className="p-6 text-center text-sm text-red-600">
        Не удалось загрузить аналитику.{' '}
        <button onClick={() => overview.refetch()} className="underline">
          Повторить
        </button>
      </div>
    )
  }
  if (overview.isLoading || !overview.data) {
    return <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
  }

  const k = overview.data?.monthlyKpis
  const created = trends.data?.days.map((d) => d.created) ?? []
  const resolved = trends.data?.days.map((d) => d.resolved) ?? []
  const createdSum = created.reduce((a, b) => a + b, 0)

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-slate-900">Аналитика</h1>
          <p className="text-xs text-slate-400">Тренды по жалобам, SLA и голосам жителей</p>
        </div>
        <div className="flex items-center gap-3">
          <PeriodSwitcher value={period} onChange={setPeriod} />
          <button
            type="button"
            disabled
            title="Скоро — экспорт PDF появится отдельно"
            className="cursor-not-allowed rounded-lg border bg-white px-3 py-1.5 text-xs font-medium text-slate-300"
          >
            Экспорт PDF
          </button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <TrendCard
          title="Решено за 7 дней"
          value={k ? `${Math.round(k.resolvedWithin7dPct)}%` : '—'}
          sub="доля жалоб, решённых в течение недели"
          series={resolved}
        />
        <TrendCard
          title="Среднее время решения"
          value={k?.avgResolutionHours == null ? '—' : `${Math.round(k.avgResolutionHours)} ч`}
          sub="за последние 30 дней"
        />
        <TrendCard
          title="Жалоб создано"
          value={trends.data == null ? '—' : String(createdSum)}
          sub="за последние 30 дней"
          series={created}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <SlaByCategory stats={sla.data ?? []} />
        <VotesImpactCard buckets={votes.data ?? []} />
      </div>
    </div>
  )
}
