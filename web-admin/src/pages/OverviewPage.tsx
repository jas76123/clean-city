import { Link } from 'react-router-dom'
import { useOperationalQuery, useBurningQuery } from '@/hooks/dashboardQueries'
import { KpiCardWithTarget } from './analytics/KpiCardWithTarget'
import { BurningQueueTable } from './overview/BurningQueueTable'
import { StatusPipeline } from './overview/StatusPipeline'

export function OverviewPage() {
  const op = useOperationalQuery()
  const burn = useBurningQuery(10)

  if (op.isError) {
    return (
      <div className="p-6 text-center text-sm text-red-600">
        Не удалось загрузить обзор.{' '}
        <button onClick={() => op.refetch()} className="underline">Повторить</button>
      </div>
    )
  }
  if (op.isLoading || !op.data) {
    return <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
  }

  const s = op.data
  const createdDelta = s.createdToday - s.createdYesterday
  const today = new Date().toISOString().slice(0, 10)

  return (
    <div className="flex flex-col gap-4 p-4">
      <section className="grid grid-cols-4 gap-4">
        <Link to="/complaints?status=NEW,IN_PROGRESS" className="block hover:opacity-90 transition-opacity">
          <div className="rounded-xl border border-slate-200 bg-white p-4">
            <div className="text-xs text-slate-500">Очередь</div>
            <div className="mt-1 text-2xl font-semibold text-slate-900">{s.backlog}</div>
            <div className="mt-1 text-xs text-slate-500">открытые жалобы сейчас</div>
          </div>
        </Link>
        <Link to="/complaints?status=NEW,IN_PROGRESS&slaBreached=true" className="block hover:opacity-90 transition-opacity">
          <div className={`rounded-xl border p-4 ${
            s.overdueNow > 0 ? 'border-rose-200 bg-rose-50' : 'border-emerald-200 bg-emerald-50'
          }`}>
            <div className="text-xs text-slate-500">Просрочено по нормативу</div>
            <div className={`mt-1 text-2xl font-semibold ${
              s.overdueNow > 0 ? 'text-rose-700' : 'text-emerald-700'
            }`}>{s.overdueNow}</div>
            <div className="mt-1 text-xs text-slate-500">нарушают норматив сейчас</div>
          </div>
        </Link>
        <KpiCardWithTarget
          label="Среднее время отклика, 24ч"
          value={s.avgDtaHours24h}
          unit="ч"
          target={s.dtaTargetHours}
          direction="lower-better"
        />
        <Link to={`/complaints?createdAfter=${today}`} className="block hover:opacity-90 transition-opacity">
          <div className="rounded-xl border border-slate-200 bg-white p-4">
            <div className="text-xs text-slate-500">Создано сегодня</div>
            <div className="mt-1 text-2xl font-semibold text-slate-900">
              {s.createdToday}
              <span className={`ml-2 text-sm font-normal ${
                createdDelta >= 0 ? 'text-rose-600' : 'text-emerald-600'
              }`}>
                {createdDelta >= 0 ? `▲ +${createdDelta}` : `▼ ${createdDelta}`}
              </span>
            </div>
            <div className="mt-1 text-xs text-slate-500">вчера было: {s.createdYesterday}</div>
          </div>
        </Link>
      </section>

      <StatusPipeline breakdown={s.statusBreakdown} />

      <section>
        <h2 className="mb-2 text-sm font-medium text-slate-700">Срочные жалобы</h2>
        {burn.isLoading
          ? <div className="text-sm text-slate-400">Загрузка…</div>
          : <BurningQueueTable items={burn.data ?? []} />
        }
      </section>
    </div>
  )
}
