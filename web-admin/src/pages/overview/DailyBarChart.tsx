import type { DailyPoint } from '@/api/types'

interface DailyBarChartProps {
  days: DailyPoint[]
}

/** CSS-график за 30 дней: пара столбиков (создано / решено) на день. */
export function DailyBarChart({ days }: DailyBarChartProps) {
  const max = Math.max(1, ...days.map((d) => Math.max(d.created, d.resolved)))
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-1 flex items-center justify-between">
        <span className="text-sm font-medium text-slate-700">Активность по дням</span>
        <span className="flex gap-3 text-xs text-slate-500">
          <span className="flex items-center gap-1">
            <span className="inline-block h-2 w-2 rounded-sm bg-sky-400" /> создано
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block h-2 w-2 rounded-sm bg-emerald-500" /> решено
          </span>
        </span>
      </div>
      {days.length === 0 ? (
        <div className="py-8 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <div className="flex h-32 items-end gap-[3px]">
          {days.map((d) => (
            <div
              key={d.date}
              className="flex h-full flex-1 items-end justify-center gap-[1px]"
              title={d.date}
            >
              <div
                className="w-1/2 rounded-sm bg-sky-400"
                style={{ height: `${(d.created / max) * 100}%` }}
              />
              <div
                className="w-1/2 rounded-sm bg-emerald-500"
                style={{ height: `${(d.resolved / max) * 100}%` }}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
