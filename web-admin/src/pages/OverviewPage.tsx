import { useOverviewQuery } from '@/hooks/complaintQueries'
import { useTopVotedQuery, useByDistrictQuery } from '@/hooks/dashboardQueries'
import { KpiCard } from '@/components/dashboard/KpiCard'
import { SlaAlertBanner } from './overview/SlaAlertBanner'
import { StatusPipeline } from './overview/StatusPipeline'
import { TopDistricts } from './overview/TopDistricts'
import { TopVotedComplaints } from './overview/TopVotedComplaints'

function fmtHours(h: number | null): string {
  return h == null ? '—' : `${Math.round(h)} ч`
}

export function OverviewPage() {
  const overview = useOverviewQuery()
  const topVoted = useTopVotedQuery()
  const districts = useByDistrictQuery('MONTH')

  if (overview.isError) {
    return (
      <div className="p-6 text-center text-sm text-red-600">
        Не удалось загрузить дашборд.{' '}
        <button onClick={() => overview.refetch()} className="underline">
          Повторить
        </button>
      </div>
    )
  }
  if (overview.isLoading || !overview.data) {
    return <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
  }

  const o = overview.data
  const k = o.monthlyKpis

  return (
    <div className="flex flex-col gap-4 p-4">
      {o.slaBreachCount > 0 && <SlaAlertBanner count={o.slaBreachCount} />}

      <div className="grid grid-cols-4 gap-4">
        <KpiCard label="Жалоб за месяц" value={String(k.total)} current={k.total} previous={k.prevTotal} />
        <KpiCard
          label="Среднее время решения"
          value={fmtHours(k.avgResolutionHours)}
          current={k.avgResolutionHours}
          previous={k.prevAvgResolutionHours}
          lowerIsBetter
        />
        <KpiCard
          label="Решено за 7 дней"
          value={`${Math.round(k.resolvedWithin7dPct)}%`}
          current={k.resolvedWithin7dPct}
          previous={k.prevResolvedWithin7dPct}
        />
        <KpiCard label="SLA-просрочки" value={String(o.slaBreachCount)} />
      </div>

      <StatusPipeline newCount={o.new} inProgress={o.inProgress} resolved={o.resolved} />

      <div className="grid grid-cols-2 gap-4">
        <TopDistricts stats={districts.data ?? []} />
        <TopVotedComplaints items={topVoted.data?.items ?? []} />
      </div>
    </div>
  )
}
