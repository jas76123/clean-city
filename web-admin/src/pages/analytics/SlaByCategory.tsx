import type { SlaStat } from '@/api/types'

interface SlaByCategoryProps {
  stats: SlaStat[]
}

/** Цвет строки по доле нарушений SLA. */
function tone(breachPct: number): { row: string; text: string; label: string } {
  if (breachPct >= 50) return { row: 'bg-red-50 border-red-200', text: 'text-red-600', label: 'Превышение' }
  if (breachPct >= 20) return { row: 'bg-amber-50 border-amber-200', text: 'text-amber-600', label: 'Близко к лимиту' }
  return { row: 'bg-emerald-50 border-emerald-200', text: 'text-emerald-600', label: 'В норме' }
}

export function SlaByCategory({ stats }: SlaByCategoryProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">SLA по категориям</div>
      {stats.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <div className="flex flex-col gap-2">
          {stats.map((s) => {
            const t = tone(s.breachPct)
            return (
              <div
                key={s.category}
                className={`flex items-center justify-between rounded-lg border px-3 py-2 ${t.row}`}
              >
                <div>
                  <div className="text-xs font-medium text-slate-700">{s.label}</div>
                  <div className="text-[10px] text-slate-400">Норматив: {s.slaHours} ч</div>
                </div>
                <div className="text-right">
                  <div className={`text-sm font-semibold ${t.text}`}>
                    {s.avgResolutionHours == null ? '—' : `${Math.round(s.avgResolutionHours)} ч`}
                  </div>
                  <div className={`text-[10px] ${t.text}`}>{t.label}</div>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
