import { useState } from 'react'
import type { AnalyticsPeriod } from '@/api/types'
import {
  useStrategicQuery,
  useByCategoryQuery, useByDistrictQuery,
  // HIDDEN_TEMPORARY: useReopenQuery, useTrendsRangeQuery, useVotesImpactQuery,
} from '@/hooks/dashboardQueries'
import { KpiCardWithTarget } from './analytics/KpiCardWithTarget'
import { MedianP90Card } from './analytics/MedianP90Card'
// HIDDEN_TEMPORARY: import { ReopenRateCard } from './analytics/ReopenRateCard'
import { PeriodSwitcher } from './analytics/PeriodSwitcher'
import { EquityTable } from './analytics/EquityTable'
// HIDDEN_TEMPORARY: import { TrendCard } from './analytics/TrendCard'
// HIDDEN_TEMPORARY: import { VotesImpactCard } from './analytics/VotesImpactCard'

// HIDDEN_TEMPORARY: groupByForPeriod used only by hidden TrendCard section
// function groupByForPeriod(period: AnalyticsPeriod): 'day' | 'week' | 'month' {
//   if (period === 'WEEK' || period === 'MONTH') return 'day'
//   if (period === 'QUARTER') return 'week'
//   return 'month'  // YEAR, ALL
// }

export function AnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>('MONTH')
  // HIDDEN_TEMPORARY: const groupBy = groupByForPeriod(period)

  const strategic = useStrategicQuery(period)
  // HIDDEN_TEMPORARY: const reopen = useReopenQuery(period)
  // HIDDEN_TEMPORARY: const trends = useTrendsRangeQuery(period, groupBy)
  const byDistrict = useByDistrictQuery(period)
  const byCategory = useByCategoryQuery(period)
  // HIDDEN_TEMPORARY: const votes = useVotesImpactQuery(period)

  if (strategic.isError) {
    return (
      <div className="p-6 text-center text-sm text-red-600">
        Не удалось загрузить аналитику.{' '}
        <button onClick={() => strategic.refetch()} className="underline">Повторить</button>
      </div>
    )
  }
  if (strategic.isLoading || !strategic.data) {
    return <div className="p-6 text-center text-sm text-slate-400">Загрузка аналитики…</div>
  }

  const k = strategic.data

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-slate-900">Аналитика</h1>
          <p className="text-xs text-slate-400">Стратегические показатели по жалобам и нормативам</p>
        </div>
        <PeriodSwitcher value={period} onChange={setPeriod} />
      </div>

      <section className="grid grid-cols-3 gap-4">
        <KpiCardWithTarget
          label="Соблюдение норматива, %"
          value={k.slaCompliancePct}
          unit="%"
          target={k.slaTargetPct}
          direction="higher-better"
        />
        <MedianP90Card
          label="Время решения"
          median={k.medianResolutionHours}
          p90={k.p90ResolutionHours}
        />
        {/* HIDDEN_TEMPORARY: ReopenRateCard
        <ReopenRateCard
          reopenRate={k.reopenRate}
          reopenCount={reopen.data?.reopenCount ?? 0}
          resolvedCount={reopen.data?.resolvedCount ?? 0}
          target={k.reopenTargetPct}
        />
        */}
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="text-xs text-slate-500">Закрыто за период</div>
          <div className="mt-1 text-2xl font-semibold text-slate-900">{k.throughput}</div>
          <div className="mt-1 text-xs text-slate-500">жалоб закрыто</div>
        </div>
      </section>

      {/* HIDDEN_TEMPORARY: section "Динамика"
      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">Динамика</h2>
        {trends.data
          ? <TrendCard trends={trends.data} />
          : <div className="text-sm text-slate-400">Загрузка…</div>}
      </section>
      */}

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">Сравнение районов</h2>
        <EquityTable items={byDistrict.data ?? []} />
      </section>

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">Категории жалоб</h2>
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-3 py-2">Категория</th>
                <th className="px-3 py-2">Жалоб</th>
                <th className="px-3 py-2">Медиана</th>
                {/* HIDDEN_TEMPORARY: <th className="px-3 py-2">p90</th> */}
                <th className="px-3 py-2">Соблюдение норматива, %</th>
              </tr>
            </thead>
            <tbody>
              {(byCategory.data ?? []).map(c => {
                const slaPct = c.slaCompliancePct
                const slaText = slaPct == null
                  ? 'text-slate-500'
                  : slaPct >= 80 ? 'text-emerald-700'
                  : slaPct >= 60 ? 'text-amber-700'
                  : 'text-rose-700'
                return (
                  <tr key={c.category} className="border-t border-slate-100">
                    <td className="px-3 py-2 text-slate-900">{c.label}</td>
                    <td className="px-3 py-2 text-slate-700">{c.count}</td>
                    <td className="px-3 py-2 text-slate-700">
                      {c.medianResolutionHours == null ? '—' : `${c.medianResolutionHours.toFixed(1)}ч`}
                    </td>
                    {/* HIDDEN_TEMPORARY: p90 column
                    <td className="px-3 py-2 text-slate-700">
                      {c.p90ResolutionHours == null ? '—' : `${c.p90ResolutionHours.toFixed(1)}ч`}
                    </td>
                    */}
                    <td className={`px-3 py-2 font-medium ${slaText}`}>
                      {slaPct == null ? '—' : `${slaPct.toFixed(0)}%`}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      {/* HIDDEN_TEMPORARY: section "Влияние голосов на скорость решения"
      <section>
        <VotesImpactCard buckets={votes.data ?? []} />
      </section>
      */}
    </div>
  )
}
